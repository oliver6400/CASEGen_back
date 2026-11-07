package com.casegen.ai.proposal.service;

import java.util.UUID;

public record AiProposalDecisionCommand(
        UUID projectId,
        UUID proposalId,
        UUID actorUserId,
        String note) {
}
