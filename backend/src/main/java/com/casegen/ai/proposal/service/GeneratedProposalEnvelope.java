package com.casegen.ai.proposal.service;

import com.casegen.ai.proposal.domain.AiProposalType;
import tools.jackson.databind.JsonNode;

public record GeneratedProposalEnvelope(AiProposalType proposalType, String title,
        String rationale, JsonNode payload) {
}
