package com.vdt.aiops.config.websocket;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vdt.aiops.agent.interact.ChatService;
import com.vdt.aiops.config.websocket.snapshot.SnapshotProvider;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/* handler for url: /ws/incidents */
@Slf4j
@Component
@RequiredArgsConstructor
public class IncidentSocketHandler extends TextWebSocketHandler {

    private final SnapshotProvider snapshotProvider;
    private final ObjectMapper objectMapper;
    private final TuiCommandService tuiCommandService;
    private final ChatService chatService;

    // To check which is connecting
    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();

    // conversationId -> session: chat only response to that TUI, not broadcast all TUI
    private final Map<String, WebSocketSession> convSessions = new ConcurrentHashMap<>();

    // After TUI connect, register it + send the initial snapshot.
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // Hold the session lock across add + snapshot read + send so a concurrent
        // broadcast() can't slip a live event onto the wire BEFORE the snapshot
        // (it blocks on the same monitor until we finish).
        // Order matters: add to the set FIRST, then read the snapshot.
        // - any event published AFTER our read -> we're already in the set -> it gets
        // broadcast to us.
        // - any event from BEFORE -> already persisted (persist happens-before
        // broadcast) -> already in the snapshot.
        // => the client always receives snapshot first, then live events. No miss, no
        // overwrite.
        try {
            synchronized (session) {
                sessions.add(session);
                TextMessage msg = new TextMessage(snapshotProvider.snapshotJson());
                if (session.isOpen()) {
                    session.sendMessage(msg);
                }
            }
        } catch (IOException e) {
            log.warn("Snapshot send fail {} : {}", session.getId(), e.getMessage());
        }
    }

    // When TUI closed connection, delete it from dict
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        convSessions.values().removeIf(s -> s == session); // remove conversation if TUI closed
    }

    // handle text message from TUI (example: change status of incident, ...)
    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            TuiCommand cmd = objectMapper.readValue(message.getPayload(), TuiCommand.class);
            String cmdRaw = cmd.getCommand();
            if ("ack".equals(cmdRaw)) {
                tuiCommandService.ack(cmd.getIncidentId());
            } else if ("resolve".equals(cmdRaw)) {
                tuiCommandService.resolve(cmd.getIncidentId());
            } else if ("ask".equals(cmdRaw)) {
                // remember which TUI ask
                if (cmd.getConversationId() != null) convSessions.put(cmd.getConversationId(), session);
                chatService.ask(cmd.getConversationId(), cmd.getText(), cmd.getIncidentId());
            } else {
                // log.warn("Unknown command: {}", cmd.command());
            }
        } catch (Exception e) {
            // log.warn("Bad command from {}: {}", session.getId(), e.getMessage());
        }
    }

    // send to TUI has this conversationId 
    public void sendTo(String conversationId, String json) {
        WebSocketSession session = convSessions.get(conversationId);
        if (session == null) return; // TUI closed/didn't ask => return
        try {
            if (session.isOpen()) {
                synchronized (session) {
                    session.sendMessage(new TextMessage(json));
                }
            }
        } catch (IOException e) {
            // log.warn("sendTo fail {}: {}", conversationId, e.getMessage());
        }
    }

    // push message to all TUI connected
    public void broadcast(String json) {
        TextMessage msg = new TextMessage(json); /* wrap it into WS Text */
        for (WebSocketSession session : sessions) { /* for each TUI */
            try {
                if (session.isOpen()) { /* if sesion is still open */
                    synchronized (session) { /* Avoid 2 thread write concurrency into 1 session */
                        session.sendMessage(msg); /* push to this TUI */
                    }
                }
            } catch (IOException e) {
                // log.warn("Send fail {} : {}", session.getId(), e.getMessage());
            }
        }
    }

}
