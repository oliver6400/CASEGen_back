package com.casegen.ai.message.service;

import com.casegen.ai.message.domain.AiMessage;

public record SendAiMessageResult(
        AiMessage userMessage,
        AiMessage assistantMessage,
        boolean replayed
) {
}
