package com.vdt.aiops.agent.loop;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vdt.aiops.agent.context.ContextBundle;
import com.vdt.aiops.agent.event.turn.AgentTurnEvent;
import com.vdt.aiops.agent.event.turn.ToolCallView;
import com.vdt.aiops.agent.incident.IncidentReport;
import com.vdt.aiops.agent.prompt.SystemPrompts;
import com.vdt.aiops.config.properties.AiopsProperties;
import com.vdt.aiops.utils.JsonExtractor;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

/* Agentic Query loop: 4 phase */
@Slf4j
@Service
@RequiredArgsConstructor
public class Query {

    private final AiopsProperties aiopsProperties;
    private final ChatModel chatModel;
    private final ToolCallingManager toolCallingManager; // layer 3: tool orchestration
    private final ToolCallback[] diagnosticTools; // list tool
    private final ObjectMapper objectMapper; // serializable -> JSON
    private final ApplicationEventPublisher eventPublisher;

    @SneakyThrows
    public List<IncidentReport> investigate(ContextBundle bundle, String investigationId) {

        /* Phase 1: Assembly Context */

        // max turn call
        int maxTurns = aiopsProperties.getAgent().getMaxTurns();

        // generate schema JSON from Incident Report and parse JSON --> object
        var converter = new BeanOutputConverter<>(
                new ParameterizedTypeReference<List<IncidentReport>>() {
                });

        // send tools to llm and disable auto-execute
        var options = ToolCallingChatOptions.builder()
                .toolCallbacks(diagnosticTools)
                .internalToolExecutionEnabled(false)
                .build();

        // prompt send to llm
        Prompt prompt = new Prompt(buildMessages(bundle, converter), options);

        /* Phase 2: Call Block*/
        ChatResponse response = chatModel.call(prompt);
        boolean needsFollowUp = response.hasToolCalls();

        /* Phase 3 + 4: Tool Excecution + Feedback -> loop */
        int turn = 0;
        while (needsFollowUp && turn < maxTurns) {
            // publish turn event
            eventPublisher.publishEvent(
                    new AgentTurnEvent(investigationId, turn + 1, toolCalls(response)));

            ToolExecutionResult exec = toolCallingManager.executeToolCalls(prompt, response);
            prompt = new Prompt(exec.conversationHistory(), options); // feedback
            response = chatModel.call(prompt); // phase 2
            needsFollowUp = response.hasToolCalls();
            turn++;
        }
        if (response.hasToolCalls()) {
            // log.warn("Hit MAX_TURNS={}", maxTurns);
            response = forceFinalAnswer(prompt);
        }

        String raw = response.getResult().getOutput().getText();
        return converter.convert(JsonExtractor.extractArray(raw)); // strip fence/preamble before parsing
    }

    @SneakyThrows
    private List<Message> buildMessages(ContextBundle bundle, BeanOutputConverter<?> converter) {
        return List.of(
                new SystemMessage(SystemPrompts.SYSTEM_PROMPT),
                new UserMessage(
                        objectMapper.writeValueAsString(bundle) + "\n\n"
                                + converter.getFormat() // gen schema JSON for List<IncidentReport>, llm based on this
                                                        // to answer
                ));
    }

    // Loop end, but still has tool call
    private ChatResponse forceFinalAnswer(Prompt lastPrompt) {
        List<Message> history = new ArrayList<>(lastPrompt.getInstructions());
        history.add(new UserMessage(
                "You have reached the tool-call budget. Do NOT request any more tools. "
                        + "Using ONLY the evidence gathered so far, output your final answer now "
                        + "as the JSON array that matches the requested schema."));
        return chatModel.call(new Prompt(history));
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
}
