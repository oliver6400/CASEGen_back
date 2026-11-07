package com.casegen.ai.proposal.review;
import com.casegen.modeling.domain.Visibility;
import java.util.UUID;
public record ReviewUpdateClassifierPayload(UUID targetClassifierId, String name,
        String documentation, UUID packageId, Visibility visibility,
        Boolean abstractClassifier, Boolean active) implements AiReviewPayload { }
