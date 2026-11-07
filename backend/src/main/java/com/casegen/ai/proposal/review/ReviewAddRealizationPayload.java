package com.casegen.ai.proposal.review;
public record ReviewAddRealizationPayload(ReviewElementRef clientClassifierRef,
        ReviewElementRef supplierRef, String documentation) implements AiReviewPayload { }
