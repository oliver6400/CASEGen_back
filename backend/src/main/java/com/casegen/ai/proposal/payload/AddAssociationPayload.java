package com.casegen.ai.proposal.payload;

import java.util.UUID;

public record AddAssociationPayload(String name, UUID packageId,
        AssociationEndProposalPayload firstEnd, AssociationEndProposalPayload secondEnd)
        implements AiProposalPayload {
}
