package com.casegen.ai.proposal.review;
import com.casegen.modeling.domain.*;
public record ReviewAssociationEndPayload(ReviewElementRef classifierRef, String roleName,
        Integer lowerBound, Integer upperBound, Boolean navigable,
        AggregationKind aggregation, AssociationEndOwnership ownership) { }
