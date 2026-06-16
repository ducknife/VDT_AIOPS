package com.vdt.aiops.agent.interact.storage;

import java.util.List;

import org.springframework.ai.chat.messages.Message;

public interface ConversationStore {
    List<Message> load(String conversationId); // read history
    void append(String conversationId, Message message); // append-row
    void replace(String conversationId, List<Message> messages); // overwrite, use after compact
}
