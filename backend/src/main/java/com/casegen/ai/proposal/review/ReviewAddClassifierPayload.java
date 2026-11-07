package com.casegen.ai.proposal.review;
import com.casegen.modeling.domain.*;
import java.util.UUID;
public record ReviewAddClassifierPayload(String name, String documentation, UUID packageId,
        ClassifierKind kind, Visibility visibility, Boolean abstractClassifier, Boolean active)
        implements AiReviewPayload { }
