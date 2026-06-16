package com.vdt.aiops.config.websocket;

import java.util.Map;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vdt.aiops.agent.event.IncidentDiagnosedEvent;
import com.vdt.aiops.agent.event.InvestigationFailedEvent;
import com.vdt.aiops.agent.event.StatusChangedEvent;
import com.vdt.aiops.agent.event.interact.ChatAnswerEvent;
import com.vdt.aiops.agent.event.interact.ChatChunkEvent;
import com.vdt.aiops.agent.event.start.InvestigationStartedEvent;
import com.vdt.aiops.agent.event.turn.AgentTurnEvent;
import com.vdt.aiops.agent.event.turn.ChatTurnEvent;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

/* Listens to agent events and pushes them to TUI clients as JSON over WebSocket. */
@Component
@RequiredArgsConstructor
public class TuiBroadcaster {

    private final IncidentSocketHandler handler;
    private final ObjectMapper objectMapper;

    @EventListener
    public void onIncidentDiagnosed(IncidentDiagnosedEvent e) {
        send("incident", e.getInvestigationId(), e.getReports());
    }

    @EventListener
    public void onTurn(AgentTurnEvent e) {
        send("turn", e.getInvestigationId(), e.getTools());
    }

    @EventListener
    public void onStarted(InvestigationStartedEvent e) {
        send("started", e.getInvestigationId(), e.getAlerts());
    }

    @EventListener
    public void onFailed(InvestigationFailedEvent e) {
        send("failed", e.getInvestigationId(), e);
    }

    // only broadcast when in transaction for annotation:
    // @TransactionalEventListener
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    // after ack/resolve commit -> start event
    public void onStatusChanged(StatusChangedEvent e) {
        send("status", Map.of(
                "incidentId", e.getIncidentId(),
                "newStatus", e.getStatus()));
    }

    // publish answer of agent
    @EventListener
    public void onChatAnswer(ChatAnswerEvent e) {
        send("answer", Map.of(
                "conversationId", e.getConversationId(),
                "text", e.getText()));
    }

    // publish chat turn event
    @EventListener
    public void onChatTurn(ChatTurnEvent e) {
        send("chat-turn", Map.of(
                "conversationId", e.getConversationId(),
                "tools", e.getTools()));
    }

    @EventListener
    public void onChatChunk(ChatChunkEvent e) {
        send("answer-chunk", Map.of(
            "conversationId", e.getConversationId(),
            "delta", e.getDelta()
        ));
    }

    /* Wrap payload in a typed envelope so the TUI knows how to handle it. */
    @SneakyThrows
    private void send(String type, String investigationId, Object data) {
        String json = objectMapper.writeValueAsString(
                TuiMessage.builder()
                        .type(type)
                        .investigationId(investigationId)
                        .data(data)
                        .build());
        handler.broadcast(json);
    }

    /* for 2 params */
    @SneakyThrows
    private void send(String type, Object data) {
        String json = objectMapper.writeValueAsString(
                TuiMessage.builder()
                        .type(type)
                        .data(data)
                        .build());
        handler.broadcast(json);
    }
}
