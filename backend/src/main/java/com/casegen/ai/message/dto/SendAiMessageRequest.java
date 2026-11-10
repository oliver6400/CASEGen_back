package com.casegen.ai.message.dto;

import com.casegen.ai.message.domain.AiSkill;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record SendAiMessageRequest(
        @NotNull UUID clientMessageId,
        @NotBlank String content,
        UUID modelId,
        UUID diagramId,
        AiSkill skill,
        String imageUrl
) {
    public SendAiMessageRequest(UUID clientMessageId, String content, UUID modelId, UUID diagramId) {
        this(clientMessageId, content, modelId, diagramId, AiSkill.GENERAL, null);
    }

    public SendAiMessageRequest(UUID clientMessageId, String content, UUID modelId, UUID diagramId, AiSkill skill) {
        this(clientMessageId, content, modelId, diagramId, skill, null);
    }
}
