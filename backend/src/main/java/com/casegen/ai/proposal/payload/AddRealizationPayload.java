package com.casegen.ai.proposal.payload;

import java.util.UUID;

public record AddRealizationPayload(UUID clientClassifierId, UUID supplierClassifierId,
        String documentation) implements AiProposalPayload {
}
