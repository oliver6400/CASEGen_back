package com.casegen.ai.proposal.dto;
import java.time.Instant;
import java.util.UUID;
public record AiProposalBatchApplyAllResponse(UUID batchId, int appliedCount,
        long finalModelRevision, Instant appliedAt) { }
