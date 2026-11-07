package com.casegen.ai.proposal.service;

import com.casegen.ai.message.domain.AiSkill;
import com.casegen.ai.proposal.domain.AiProposalType;
import com.casegen.ai.proposal.payload.AiProposalPayload;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

public record GeneratedAiProposal(AiProposalType proposalType, String title, String rationale,
        AiProposalPayload payload, JsonNode canonicalPayload, long baseModelRevision,
        Long baseDiagramRevision, String provider, AiSkill skill, UUID modelId,
        UUID diagramId, UUID sourceMessageId) {

    public GeneratedAiProposal {
        canonicalPayload = canonicalPayload.deepCopy();
    }

    @Override
    public JsonNode canonicalPayload() {
        return canonicalPayload.deepCopy();
    }
}
