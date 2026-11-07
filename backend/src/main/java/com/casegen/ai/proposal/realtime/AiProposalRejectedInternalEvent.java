package com.casegen.ai.proposal.realtime;

import java.time.Instant;
import java.util.UUID;

public record AiProposalRejectedInternalEvent(
        UUID projectId,
        UUID proposalId,
        Instant occurredAt) {
}
