package com.casegen.ai.proposal.review;

import com.casegen.ai.proposal.domain.AiProposalBatch;

public record CreatedAiProposalBatch(AiProposalBatch batch, boolean replayed) {
}
