package com.casegen.ai.proposal.payload;

import java.util.UUID;

public record AddGeneralizationPayload(UUID specificClassifierId, UUID generalClassifierId,
        Boolean substitutable, String documentation) implements AiProposalPayload {
}
