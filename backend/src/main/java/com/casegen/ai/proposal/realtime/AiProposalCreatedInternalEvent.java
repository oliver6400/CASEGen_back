package com.casegen.ai.proposal.realtime;

import java.time.Instant;
import java.util.UUID;

public record AiProposalCreatedInternalEvent(
        UUID projectId,
        UUID proposalId,
        Instant occurredAt
) {
}
