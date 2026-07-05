package com.vdt.aiops.agent.interact.storage;

import java.util.List;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * Persist chat history in PostgreSQL so conversation context survives engine restarts.
 * Chosen as the @Primary {@link ConversationStore}; {@link InMemoryConversationStore}
 * remains a fallback. Message order is the auto-increment id (no seq bookkeeping).
 */
@Component
@Primary
@RequiredArgsConstructor
public class JdbcConversationStore implements ConversationStore {

    private final JdbcTemplate jdbc;

    @Override
    public List<Message> load(String conversationId) {
        return jdbc.query(
                "SELECT role, content FROM chat_messages WHERE conversation_id = ? ORDER BY id ASC",
                (rs, i) -> toMessage(rs.getString("role"), rs.getString("content")),
                conversationId);
    }

    @Override
    public void append(String conversationId, Message message) {
        jdbc.update(
                "INSERT INTO chat_messages(conversation_id, role, content) VALUES (?, ?, ?)",
                conversationId, role(message), text(message));
    }

    // overwrite whole history (used after compact): delete + re-insert atomically
    @Override
    @Transactional
    public void replace(String conversationId, List<Message> messages) {
        jdbc.update("DELETE FROM chat_messages WHERE conversation_id = ?", conversationId);
        for (Message m : messages) {
            append(conversationId, m);
        }
    }

    // only user/assistant/system text messages are ever stored here
    private static String role(Message m) {
        if (m instanceof AssistantMessage) return "assistant";
        if (m instanceof SystemMessage) return "system";
        return "user";
    }

    private static String text(Message m) {
        return m.getText() == null ? "" : m.getText();
    }

    private static Message toMessage(String role, String content) {
        String r = role == null ? "" : role.toLowerCase();
        return switch (r) {
            case "assistant" -> new AssistantMessage(content);
            case "system" -> new SystemMessage(content);
            default -> new UserMessage(content); // user + fallback
        };
    }
}
