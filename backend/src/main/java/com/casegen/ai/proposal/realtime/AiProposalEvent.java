package com.casegen.ai.proposal.realtime;

import com.casegen.ai.proposal.dto.AiProposalResponse;
import java.time.Instant;
import java.util.UUID;

public record AiProposalEvent(
        UUID eventId,
        String eventType,
        UUID projectId,
        AiProposalResponse proposal,
        Instant occurredAt
) {
    public static final String PROPOSAL_CREATED = "AI_PROPOSAL_CREATED";
    public static final String PROPOSAL_APPLIED = "AI_PROPOSAL_APPLIED";
    public static final String PROPOSAL_REJECTED = "AI_PROPOSAL_REJECTED";
}
