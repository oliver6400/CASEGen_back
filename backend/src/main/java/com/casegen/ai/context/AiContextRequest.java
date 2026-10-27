package com.casegen.ai.context;

import com.casegen.ai.message.domain.AiSkill;

import java.util.UUID;

public record AiContextRequest(
        UUID projectId,
        UUID sessionId,
        UUID modelId,
        UUID diagramId,
        String currentUserContent,
        UUID currentMessageId,
        AiSkill skill
) {
    public AiContextRequest {
        if (skill == null) skill = AiSkill.GENERAL;
    }

    public AiContextRequest(UUID projectId, UUID sessionId, UUID modelId, UUID diagramId,
            String currentUserContent, UUID currentMessageId) {
        this(projectId, sessionId, modelId, diagramId, currentUserContent, currentMessageId,
                AiSkill.GENERAL);
    }
}
