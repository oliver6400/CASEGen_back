package com.casegen.ai.proposal.realtime;

import java.time.Instant;
import java.util.UUID;

public record AiProposalBatchCreatedEvent(UUID eventId, String eventType, UUID projectId,
        UUID batchId, Instant occurredAt) {
    public static final String EVENT_TYPE="AI_PROPOSAL_BATCH_CREATED";
}
