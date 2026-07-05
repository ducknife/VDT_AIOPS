package com.vdt.aiops.agent.loop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.ApplicationEventPublisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vdt.aiops.agent.context.ContextBundle;
import com.vdt.aiops.agent.event.turn.AgentTurnEvent;
import com.vdt.aiops.agent.incident.IncidentReport;
import com.vdt.aiops.config.properties.AiopsProperties;

/**
 * Unit tests for the agent's DETERMINISTIC scaffolding (loop control, MAX_TURNS guard, output
 * parsing) with a MOCKED {@link ChatModel} — no real LLM call. The LLM's diagnostic JUDGEMENT
 * (severity/root-cause quality) is validated separately by the empirical benchmark, not here.
 */
class QueryTest {

    private ChatModel chatModel;
    private ToolCallingManager toolCallingManager;
    private ApplicationEventPublisher eventPublisher;
    private AiopsProperties props;
    private Query query;

    @BeforeEach
    void setUp() {
        chatModel = mock(ChatModel.class);
        toolCallingManager = mock(ToolCallingManager.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        props = new AiopsProperties();
        props.getAgent().setMaxTurns(8);
        query = new Query(props, chatModel, toolCallingManager,
                new ToolCallback[0], new ObjectMapper(), eventPublisher);
    }

    // Instants left null so the plain ObjectMapper (no JavaTimeModule) can serialize the bundle.
    private ContextBundle bundle() {
        return ContextBundle.builder().focus("redis").build();
    }

    // a model response that requests one tool (mocked — the tool-call constructor is not public)
    private ChatResponse toolResponse() {
        AssistantMessage.ToolCall call =
                new AssistantMessage.ToolCall("id-1", "function", "getServiceLogs", "{\"service\":\"redis\"}");
        AssistantMessage msg = mock(AssistantMessage.class);
        when(msg.hasToolCalls()).thenReturn(true);
        when(msg.getToolCalls()).thenReturn(List.of(call));
        Generation gen = mock(Generation.class);
        when(gen.getOutput()).thenReturn(msg);
        ChatResponse resp = mock(ChatResponse.class);
        when(resp.hasToolCalls()).thenReturn(true);
        when(resp.getResults()).thenReturn(List.of(gen));
        return resp;
    }

    // a final answer (no tool calls) carrying the given text
    private ChatResponse finalResponse(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    private void stubToolExecution() {
        ToolExecutionResult exec = mock(ToolExecutionResult.class);
        when(exec.conversationHistory()).thenReturn(List.of(new UserMessage("tool observation")));
        when(toolCallingManager.executeToolCalls(any(), any())).thenReturn(exec);
    }

    @Test
    void immediateAnswer_parsesReportsWithASingleModelCall() {
        when(chatModel.call(any(Prompt.class))).thenReturn(finalResponse("[{\"service\":\"redis\",\"title\":\"oom\"}]"));

        List<IncidentReport> out = query.investigate(bundle(), "inv-1");

        assertThat(out).hasSize(1);
        assertThat(out.get(0).getService()).isEqualTo("redis");
        verify(chatModel, times(1)).call(any(Prompt.class));
        verify(toolCallingManager, never()).executeToolCalls(any(), any());
    }

    @Test
    void oneToolRoundThenAnswer_executesToolsAndParses() {
        stubToolExecution();
        // build responses BEFORE stubbing call() — toolResponse() itself uses when(), which must
        // not run while the chatModel.call stubbing is still open (avoids UnfinishedStubbing).
        ChatResponse tool = toolResponse();
        ChatResponse fin = finalResponse("[{\"service\":\"postgres\",\"title\":\"down\"}]");
        when(chatModel.call(any(Prompt.class))).thenReturn(tool, fin);

        List<IncidentReport> out = query.investigate(bundle(), "inv-2");

        assertThat(out).hasSize(1);
        assertThat(out.get(0).getService()).isEqualTo("postgres");
        verify(chatModel, times(2)).call(any(Prompt.class));
        verify(toolCallingManager, times(1)).executeToolCalls(any(), any());
        // one turn event published for the single tool round
        verify(eventPublisher, times(1)).publishEvent(any(AgentTurnEvent.class));
    }

    @Test
    void maxTurnsReached_forcesFinalAnswerAndStillParses() {
        props.getAgent().setMaxTurns(2);
        stubToolExecution();
        // model keeps asking for tools; the FORCED final call returns the answer.
        // Pre-build all responses (toolResponse() uses when() internally).
        ChatResponse t1 = toolResponse();
        ChatResponse t2 = toolResponse();
        ChatResponse t3 = toolResponse();
        ChatResponse fin = finalResponse("[{\"service\":\"nginx\",\"title\":\"5xx\"}]");
        when(chatModel.call(any(Prompt.class))).thenReturn(t1, t2, t3, fin);

        List<IncidentReport> out = query.investigate(bundle(), "inv-3");

        assertThat(out).hasSize(1);
        assertThat(out.get(0).getService()).isEqualTo("nginx");
        // 1 initial + 2 loop turns + 1 forced final = 4 model calls
        verify(chatModel, times(4)).call(any(Prompt.class));
        verify(toolCallingManager, times(2)).executeToolCalls(any(), any());
    }

    @Test
    void proseWrappedAnswer_isStrippedAndParsed() {
        String prose = "Sure — based on the evidence:\n```json\n"
                + "[{\"service\":\"redis\",\"title\":\"oom\"}]\n```\nThat is my final answer.";
        when(chatModel.call(any(Prompt.class))).thenReturn(finalResponse(prose));

        List<IncidentReport> out = query.investigate(bundle(), "inv-4");

        assertThat(out).hasSize(1);
        assertThat(out.get(0).getService()).isEqualTo("redis");
    }

    @Test
    void unparseableAnswer_yieldsEmptyListInsteadOfThrowing() {
        when(chatModel.call(any(Prompt.class))).thenReturn(finalResponse("I could not find any incident."));

        List<IncidentReport> out = query.investigate(bundle(), "inv-5");

        assertThat(out).isEmpty();
    }
}
