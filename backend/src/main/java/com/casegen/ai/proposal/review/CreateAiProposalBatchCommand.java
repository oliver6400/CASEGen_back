package com.casegen.ai.proposal.review;

import java.util.UUID;

public record CreateAiProposalBatchCommand(
        UUID projectId,
        UUID sessionId,
        UUID sourceMessageId,
        UUID actorUserId,
        UUID clientBatchId) {
}
