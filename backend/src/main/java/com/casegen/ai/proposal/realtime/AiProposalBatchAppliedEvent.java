package com.casegen.ai.proposal.realtime;
import java.time.Instant;
import java.util.UUID;
public record AiProposalBatchAppliedEvent(UUID eventId, String eventType, UUID projectId,
        UUID batchId, int appliedCount, long finalModelRevision, Instant occurredAt) {
    public static final String EVENT_TYPE = "AI_PROPOSAL_BATCH_APPLIED";
}
