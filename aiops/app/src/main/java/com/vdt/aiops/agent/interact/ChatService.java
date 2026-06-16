package com.vdt.aiops.agent.interact;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.MessageAggregator;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vdt.aiops.agent.event.interact.ChatAnswerEvent;
import com.vdt.aiops.agent.event.interact.ChatChunkEvent;
import com.vdt.aiops.agent.event.turn.ChatTurnEvent;
import com.vdt.aiops.agent.event.turn.ToolCallView;
import com.vdt.aiops.agent.incident.IncidentRepository;
import com.vdt.aiops.agent.interact.defense.AutoCompact;
import com.vdt.aiops.agent.interact.storage.ConversationStore;
import com.vdt.aiops.agent.prompt.SystemPrompts;
import com.vdt.aiops.config.properties.AiopsProperties;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ConversationStore store;
    private final ChatModel chatModel;
    private final ToolCallingManager toolCallingManager;
    private final ToolCallback[] diagnosticTools;
    private final IncidentRepository incidentRepository;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final AiopsProperties aiopsProperties; // get max turn
    private final Semaphore chatSemaphore;
    private final AutoCompact autoCompact;

    @Async("chatExecutor")
    public void ask(String conversationId, String text, Long incidentId) {

        var options = ToolCallingChatOptions.builder()
                .toolCallbacks(diagnosticTools)
                .internalToolExecutionEnabled(false)
                .build();

        try {
            chatSemaphore.acquire();
            try {

                autoCompact.compactIfNeeded(conversationId);
                List<Message> messages = buildMessages(conversationId, text, incidentId);
                Prompt prompt = new Prompt(messages, options);
                ChatResponse response = streamCall(conversationId, prompt);
                boolean needsFollowUp = response.hasToolCalls();

                int turn = 0;
                int maxTurns = aiopsProperties.getAgent().getMaxTurns();
                while (needsFollowUp && turn < maxTurns) {
                    eventPublisher.publishEvent(
                        new ChatTurnEvent(conversationId, toolCalls(response))
                    );
                    ToolExecutionResult exec = toolCallingManager.executeToolCalls(prompt, response);
                    prompt = new Prompt(exec.conversationHistory(), options);
                    response = streamCall(conversationId, prompt);
                    needsFollowUp = response.hasToolCalls();
                    turn++;
                }

                if (response.hasToolCalls()) {
                    response = forceFinalAnswer(conversationId, prompt);
                }

                String answer = response.getResult().getOutput().getText();

                // soft persist
                store.append(conversationId, new UserMessage(text));
                store.append(conversationId, new AssistantMessage(answer));

                // publish the answer
                eventPublisher.publishEvent(
                        new ChatAnswerEvent(conversationId, answer));

            } finally {
                // release the thread
                chatSemaphore.release();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            // log.error("chat ask failed conv={}: {}", conversationId, e.getMessage());
            eventPublisher.publishEvent(
                    new ChatAnswerEvent(conversationId, "Sorry, I can't handle this question"));
        }
    }

    // build messages for chat
    private List<Message> buildMessages(String conversationId, String text, Long incidentId) {
        List<Message> history = store.load(conversationId);
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(SystemPrompts.CHAT_SYSTEM_PROMPT));

        // seed incident: only 1st turn (history is Empty) and has incidentId
        if (incidentId != null) {
            incidentRepository.findById(incidentId).ifPresent(
                    inc -> messages.add(new UserMessage(
                            "Context - the incident under discussion:\n" +
                                    toJson(inc))));
        }

        messages.addAll(history);
        messages.add(new UserMessage(text));
        return messages;
    }

    // stream API call
    private ChatResponse streamCall(String conversationId, Prompt prompt) {
        AtomicReference<ChatResponse> full = new AtomicReference<>();
        new MessageAggregator()
            .aggregate(chatModel.stream(prompt), full::set) // group all chunk -> response at last
            .doOnNext(chunk -> {
                if (chunk.getResult() == null || chunk.getResult().getOutput() == null) return;
                String delta = chunk.getResult().getOutput().getText(); // get new tokens
                if (delta != null && !delta.isEmpty()) {
                    eventPublisher.publishEvent(
                        new ChatChunkEvent(conversationId, delta)
                    );
                }
            })
            .blockLast(); // block current thread untils stream completed and activate stream
        return full.get();
    }

    // force final anwser if max turns reached
    private ChatResponse forceFinalAnswer(String conversationId, Prompt lastPrompt) {
        List<Message> history = new ArrayList<>(lastPrompt.getInstructions());
        history.add(new UserMessage(
                "You have reached the tool-call budget. Do NOT request any more tools. "
                        + "Using only what you've gathered so far, answer the operator now in plain, "
                        + "concise natural language."));
        return streamCall(conversationId, new Prompt(history));
    }

    private List<ToolCallView> toolCalls(ChatResponse response) {
        return response.getResults().stream()
                .flatMap(r -> r.getOutput().getToolCalls().stream())
                .map(tc -> ToolCallView.builder()
                        .name(tc.name())
                        .type(tc.type())
                        .arguments(parseArgs(tc.arguments()))
                        .build())
                .toList();
    }

    // parse arguments String include json to map
    private Object parseArgs(String raw) {
        if (raw == null || raw.isBlank())
            return Map.of();
        try {
            return objectMapper.readValue(raw, Map.class);
        } catch (Exception e) {
            return raw;
        }
    }

    @SneakyThrows
    public String toJson(Object o) {
        return objectMapper.writeValueAsString(o);
    }
}
