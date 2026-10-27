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
        AiSkill skill
) {
    public SendAiMessageRequest(UUID clientMessageId, String content, UUID modelId, UUID diagramId) {
        this(clientMessageId, content, modelId, diagramId, AiSkill.GENERAL);
    }
}
