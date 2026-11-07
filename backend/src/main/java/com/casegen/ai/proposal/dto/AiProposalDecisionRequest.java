package com.casegen.ai.proposal.dto;

import jakarta.validation.constraints.Size;

public record AiProposalDecisionRequest(@Size(max = 1000) String note) {
}
