package com.casegen.ai.proposal.dto;

import com.casegen.ai.proposal.domain.AiProposalStatus;
import com.casegen.ai.proposal.domain.AiProposalType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

public record AiProposalBatchChildResponse(UUID id, UUID batchId, int batchOrder,
        String clientRef, AiProposalType proposalType, String title, String rationale,
        AiProposalStatus status, JsonNode payload, int payloadSchemaVersion,
        long baseModelRevision, Long appliedModelRevision, UUID appliedElementId,
        AiProposalActorResponse requestedBy, AiProposalActorResponse decidedBy,
        Instant decidedAt, String decisionNote, Instant createdAt,
        List<AiProposalBatchDependencyResponse> dependencies,
        boolean blocked, String blockedReason) { }
