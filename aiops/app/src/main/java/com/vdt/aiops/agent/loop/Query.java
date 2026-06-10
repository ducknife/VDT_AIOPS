package com.vdt.aiops.agent.loop;

import java.util.ArrayList;
import java.util.List;

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
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vdt.aiops.agent.context.ContextBundle;
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

    @SneakyThrows
    public List<IncidentReport> investigate(ContextBundle bundle) {

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

        // TODO: compact check (token budget) - defer, AutoCompact stub

        /* Phase 2: Stream API Call: P A: temp: call block */
        // TODO: Stream call when build TUI
        ChatResponse response = chatModel.call(prompt);
        boolean needsFollowUp = response.hasToolCalls();

        /* Phase 3 + 4: Tool Excecution + Feedback -> loop */
        int turn = 0;
        while (needsFollowUp && turn < maxTurns) {
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
        String json = JsonExtractor.extractArray(raw); // strip fence/preamble before parsing
        return converter.convert(json);
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

    private ChatResponse forceFinalAnswer(Prompt lastPrompt) {
        List<Message> history = new ArrayList<>(lastPrompt.getInstructions());
        history.add(new UserMessage(
                "You have reached the tool-call budget. Do NOT request any more tools. "
                        + "Using ONLY the evidence gathered so far, output your final answer now "
                        + "as the JSON array that matches the requested schema."));
        return chatModel.call(new Prompt(history));
    }
}
