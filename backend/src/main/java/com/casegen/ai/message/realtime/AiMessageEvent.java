package com.casegen.ai.message.realtime;

import com.casegen.ai.message.dto.AiMessageResponse;
import java.time.Instant;
import java.util.UUID;

public record AiMessageEvent(
        UUID eventId,
        String eventType,
        UUID projectId,
        UUID sessionId,
        AiMessageResponse message,
        Instant occurredAt
) {
    public static final String MESSAGE_CREATED = "AI_MESSAGE_CREATED";
}
