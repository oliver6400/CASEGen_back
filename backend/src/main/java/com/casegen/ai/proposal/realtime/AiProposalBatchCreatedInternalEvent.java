package com.casegen.ai.proposal.realtime;

import java.time.Instant;
import java.util.UUID;

public record AiProposalBatchCreatedInternalEvent(UUID projectId, UUID batchId,
        Instant occurredAt) { }
