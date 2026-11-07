package com.casegen.ai.proposal.realtime;

import java.time.Instant;
import java.util.UUID;

public record AiProposalAppliedInternalEvent(
        UUID projectId,
        UUID proposalId,
        Instant occurredAt) {
}
