package com.vdt.aiops.agent.interact.history;

import java.sql.Timestamp;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/**
 * Read-only browser over the persisted chat history (table {@code chat_messages}),
 * backing the TUI's {@code /conversation-history} command.
 */
@Service
@RequiredArgsConstructor
public class ConversationBrowser {

    private final JdbcTemplate jdbc;

    /** All conversations, newest activity first, with a one-line preview (the first user question). */
    public List<ConversationSummary> list() {
        return jdbc.query("""
                SELECT m.conversation_id AS cid,
                       count(*)          AS n,
                       max(m.created_at) AS last_at,
                       (SELECT u.content FROM chat_messages u
                         WHERE u.conversation_id = m.conversation_id AND u.role = 'user'
                         ORDER BY u.id ASC LIMIT 1) AS preview
                FROM chat_messages m
                GROUP BY m.conversation_id
                ORDER BY last_at DESC
                LIMIT 50
                """,
                (rs, i) -> {
                    Timestamp ts = rs.getTimestamp("last_at");
                    return new ConversationSummary(
                            rs.getString("cid"),
                            rs.getLong("n"),
                            rs.getString("preview"),
                            ts != null ? ts.toInstant() : null);
                });
    }

    /** Full message list of one conversation, in order. */
    public List<ChatMessageView> get(String conversationId) {
        return jdbc.query(
                "SELECT role, content FROM chat_messages WHERE conversation_id = ? ORDER BY id ASC",
                (rs, i) -> new ChatMessageView(rs.getString("role"), rs.getString("content")),
                conversationId);
    }
}
