package com.casegen.ai.proposal.review;

public sealed interface AiReviewPayload permits ReviewAddClassifierPayload,
        ReviewAddAttributePayload, ReviewAddOperationPayload, ReviewAddAssociationPayload,
        ReviewAddGeneralizationPayload, ReviewAddRealizationPayload,
        ReviewUpdateClassifierPayload, ReviewUpdateAttributePayload { }
