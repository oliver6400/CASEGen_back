package com.casegen.ai.proposal.dto;

import com.casegen.ai.message.domain.AiSkill;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AiProposalBatchResponse(UUID id, UUID projectId, UUID sessionId,
        UUID sourceMessageId, AiProposalActorResponse requestedBy, UUID modelId,
        String modelName, UUID diagramId, String diagramName, String title, String summary,
        long baseModelRevision, Long baseDiagramRevision, long modelRevisionCursor,
        long currentModelRevision, String provider, AiSkill skill, Instant createdAt,
        AiProposalActorResponse appliedBy, Instant appliedAt, String derivedStatus,
        List<AiProposalBatchChildResponse> children) { }
