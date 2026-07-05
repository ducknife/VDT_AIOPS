package com.vdt.aiops.agent.interact.history;

/** One message when replaying a stored conversation: role (user|assistant|system) + text. */
public record ChatMessageView(String role, String text) {
}
