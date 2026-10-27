package com.casegen.ai.provider;

import java.util.Objects;

public record AiPromptMessage(AiPromptRole role, String content) {

    public AiPromptMessage {
        Objects.requireNonNull(role, "role is required");
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content must not be blank");
        }
    }
}
