package com.casegen.ai.proposal.dto;

import com.casegen.ai.message.domain.AiSkill;
import com.casegen.ai.proposal.domain.AiProposalStatus;
import com.casegen.ai.proposal.domain.AiProposalType;
import java.time.Instant;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

public record AiProposalResponse(UUID id, UUID batchId, UUID projectId, UUID sessionId, UUID sourceMessageId,
        UUID requestedByUserId, String requestedByName, AiProposalStatus status,
        AiProposalType proposalType, String title, String rationale, JsonNode payload,
        int payloadSchemaVersion, UUID modelId, String modelName, UUID diagramId,
        String diagramName, Long baseModelRevision, Long baseDiagramRevision,
        Long currentModelRevision, Long currentDiagramRevision, boolean modelStale,
        boolean diagramContextChanged, UUID decidedByUserId, String decidedByName,
        Instant decidedAt, String decisionNote, Long appliedModelRevision,
        UUID appliedElementId, String provider, AiSkill skill,
        Instant createdAt, Instant updatedAt) {

    public AiProposalResponse {
        payload = payload.deepCopy();
    }

    @Override
    public JsonNode payload() {
        return payload.deepCopy();
    }
}
