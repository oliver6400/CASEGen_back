package com.casegen.ai.proposal.review;
import com.casegen.modeling.domain.Visibility;
public record ReviewAddOperationPayload(ReviewElementRef ownerRef, String name, String documentation,
        Visibility visibility, Boolean abstractOperation, Boolean staticOperation, Boolean query)
        implements AiReviewPayload { }
