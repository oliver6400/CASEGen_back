package com.casegen.ai.message.dto;

import com.casegen.ai.message.domain.AiMessageRole;
import com.casegen.ai.message.domain.AiSkill;
import java.time.Instant;
import java.util.UUID;

public record AiMessageResponse(
        UUID id,
        UUID sessionId,
        AiMessageRole role,
        AiSkill skill,
        UUID authorUserId,
        String authorName,
        String content,
        UUID modelId,
        String modelName,
        UUID diagramId,
        String diagramName,
        String provider,
        UUID replyToMessageId,
        Instant createdAt
) {
}
