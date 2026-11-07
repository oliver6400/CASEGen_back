package com.casegen.ai.proposal.review;
import java.util.UUID;
public record ReviewAddAssociationPayload(String name, String documentation, UUID packageId,
        ReviewAssociationEndPayload firstEnd, ReviewAssociationEndPayload secondEnd)
        implements AiReviewPayload { }
