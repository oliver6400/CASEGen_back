package com.casegen.ai.message.dto;

public record SendAiMessageResponse(
        AiMessageResponse userMessage,
        AiMessageResponse assistantMessage,
        boolean replayed
) {
}
