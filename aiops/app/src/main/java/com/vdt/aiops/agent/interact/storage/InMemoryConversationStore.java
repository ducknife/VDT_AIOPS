package com.vdt.aiops.agent.interact.storage;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.ai.chat.messages.Message;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class InMemoryConversationStore implements ConversationStore {

    // multi-conversation run concurrency
    private final Map<String, Conversation> store = new ConcurrentHashMap<>();
    private static final Duration TTL = Duration.ofMinutes(30); // if conversation idle for 30 minutes -> delete

    @Override 
    public List<Message> load(String conversationId) {
        Conversation c = store.get(conversationId);
        if (c == null) return List.of();
        c.lastAccess = Instant.now();
        return c.messages;
    }

    // with CopyOnWriteArrayList(): Every Thread can read main array concurrency
    // but CUD must serialize, and it do on copied-array (bản sao) 
    @Override
    public void append(String conversationId, Message message) {
        touch(conversationId).messages.add(message);
    }

    // replace after compact
    @Override
    public void replace(String conversationId, List<Message> messages) {
        Conversation c = touch(conversationId);
        c.messages.clear();
        c.messages.addAll(messages);
    }

    /* Wipe out conversations idle more than TTL */
    @Scheduled(fixedDelay = 5 * 60 * 1000) // every 5 minutes
    public void evictIdle() {
        Instant cutoff = Instant.now().minus(TTL);
        store.entrySet().removeIf(e -> e.getValue().lastAccess.isBefore(cutoff));
    }

    private Conversation touch(String conversationId) {
        Conversation c = store.computeIfAbsent(conversationId, k -> new Conversation());
        c.lastAccess = Instant.now();
        return c;
    }

    private static class Conversation {
        final List<Message> messages = new CopyOnWriteArrayList<>();
        volatile Instant lastAccess = Instant.now();
    }
}
