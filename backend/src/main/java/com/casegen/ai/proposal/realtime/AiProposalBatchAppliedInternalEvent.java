package com.casegen.ai.proposal.realtime;
import java.time.Instant;
import java.util.UUID;
public record AiProposalBatchAppliedInternalEvent(UUID projectId, UUID batchId,
        int appliedCount, long finalModelRevision, Instant occurredAt) { }
