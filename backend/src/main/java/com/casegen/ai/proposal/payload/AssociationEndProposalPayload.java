package com.casegen.ai.proposal.payload;

import com.casegen.modeling.domain.AggregationKind;
import com.casegen.modeling.domain.AssociationEndOwnership;
import java.util.UUID;

public record AssociationEndProposalPayload(UUID classifierId, String roleName,
        Integer lowerBound, Integer upperBound, Boolean navigable,
        AggregationKind aggregation, AssociationEndOwnership ownership) {
}
