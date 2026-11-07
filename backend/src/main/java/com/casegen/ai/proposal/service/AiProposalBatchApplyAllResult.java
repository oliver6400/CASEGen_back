package com.casegen.ai.proposal.service;
import java.time.Instant;
import java.util.UUID;
public record AiProposalBatchApplyAllResult(UUID batchId, int appliedCount,
        long finalModelRevision, Instant appliedAt) { }
