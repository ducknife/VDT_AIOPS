package com.vdt.aiops.config.websocket;

import java.util.Map;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vdt.aiops.agent.event.IncidentDiagnosedEvent;
import com.vdt.aiops.agent.event.IncidentFeedbackEvent;
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
        send("incident", e.getInvestigationId(), e.getIncidents());
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

    // human feedback saved -> broadcast so every TUI shows the verdict badge
    @EventListener
    public void onFeedback(IncidentFeedbackEvent e) {
        send("feedback", Map.of(
                "incidentId", e.getIncidentId(),
                "verdict", e.getVerdict()));
    }

    // CHAT events -> only response exactly TUI ask, not broadcast
    @EventListener
    public void onChatAnswer(ChatAnswerEvent e) {
        sendToConversation("answer", e.getConversationId(), Map.of(
                "conversationId", e.getConversationId(),
                "text", e.getText()));
    }

    @EventListener
    public void onChatTurn(ChatTurnEvent e) {
        sendToConversation("chat-turn", e.getConversationId(), Map.of(
                "conversationId", e.getConversationId(),
                "tools", e.getTools()));
    }

    @EventListener
    public void onChatChunk(ChatChunkEvent e) {
        sendToConversation("answer-chunk", e.getConversationId(), Map.of(
                "conversationId", e.getConversationId(),
                "delta", e.getDelta()));
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

    /* CHAT: send to exactly TUI ask, not broadcast */
    @SneakyThrows
    private void sendToConversation(String type, String conversationId, Object data) {
        String json = objectMapper.writeValueAsString(
                TuiMessage.builder()
                        .type(type)
                        .data(data)
                        .build());
        handler.sendTo(conversationId, json);
    }
}
