package com.casegen.ai.proposal.review;
public record ReviewAddGeneralizationPayload(ReviewElementRef specificRef,
        ReviewElementRef generalRef, Boolean substitutable, String documentation)
        implements AiReviewPayload { }
