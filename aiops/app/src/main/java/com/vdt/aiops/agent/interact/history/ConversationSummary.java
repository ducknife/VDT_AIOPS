package com.vdt.aiops.agent.interact.history;

import java.time.Instant;

/** One row in the /conversation-history browser: id + how many messages + first-question preview + last activity. */
public record ConversationSummary(
        String conversationId,
        long messageCount,
        String preview,
        Instant lastAt) {
}
