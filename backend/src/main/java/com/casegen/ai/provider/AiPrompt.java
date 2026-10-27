package com.casegen.ai.provider;

import java.util.List;

public record AiPrompt(String systemInstruction, List<AiPromptMessage> messages) {

    public AiPrompt {
        systemInstruction = systemInstruction == null ? "" : systemInstruction.trim();
        messages = messages == null ? List.of() : List.copyOf(messages);
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("messages must not be empty");
        }
    }
}
