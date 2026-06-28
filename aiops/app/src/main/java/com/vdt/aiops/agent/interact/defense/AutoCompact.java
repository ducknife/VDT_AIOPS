package com.vdt.aiops.agent.interact.defense;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import com.vdt.aiops.agent.interact.storage.ConversationStore;

import lombok.RequiredArgsConstructor;

/* Auto - compact if conversation too long. */
@Service
@RequiredArgsConstructor
public class AutoCompact {

    private static final int COMPACT_THRESHOLD = 200000;
    private static final int RECENT_KEEP = 6; // keep 6 latest message, else -> compact
    private final ConversationStore store;
    private final ChatModel chatModel;

    public void compactIfNeeded(String conversationId) {
        try {
            List<Message> history = store.load(conversationId);
            if (history.size() <= RECENT_KEEP)
                return;
            if (estimateTokens(history) < COMPACT_THRESHOLD)
                return;

            int cut = history.size() - RECENT_KEEP;
            List<Message> old = new ArrayList<>(history.subList(0, cut));
            List<Message> recent = new ArrayList<>(history.subList(cut, history.size()));

            // summarize old message
            String summary = summarize(old);

            // replace history to summary
            List<Message> compacted = new ArrayList<>();
            compacted.add(new UserMessage("Summary of earlier conversation:\n" + summary));
            compacted.addAll(recent);
            store.replace(conversationId, compacted); // overwrite history
            
        } catch (Exception e) {
            // log.warn("compact failed, skip: {}", e.getMessage());
        }
    }

    // call llm to summarize
    private String summarize(List<Message> messages) {
        String convo = messages.stream()
                .map(Message::getText)
                .collect(Collectors.joining("\n"));
        Prompt prompt = new Prompt(List.of(
                new SystemMessage("Summarize this conversation concisely. Preserve key facts, "
                        + "findings, service names, and any decisions. Drop chit-chat."),
                new UserMessage(convo))); // no tool because only investigate
        return chatModel.call(prompt).getResult().getOutput().getText();
    }

    // check if need to compact
    private int estimateTokens(List<Message> messages) {
        int chars = messages.stream()
                .mapToInt(m -> m.getText() == null ? 0 : m.getText().length())
                .sum();
        return chars / 4; // 1 token ~ 4 chars ==> total_chars / 4 = total tokens
    }
}
