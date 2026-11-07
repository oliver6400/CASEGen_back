package com.casegen.ai.proposal.service;

import com.casegen.ai.proposal.domain.AiProposal;

public record CreateAiProposalResult(AiProposal proposal, boolean replayed) {
}
