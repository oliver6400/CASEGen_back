package com.casegen.ai.proposal.review;
import com.casegen.modeling.domain.*;
import java.util.UUID;
public record ReviewUpdateAttributePayload(UUID targetPropertyId, String name,
        String documentation, Visibility visibility, TypeKind typeKind,
        PrimitiveType primitiveType, ReviewElementRef typeClassifierRef,
        Integer lowerBound, Integer upperBound, Boolean staticProperty,
        Boolean readOnly, Boolean derived) implements AiReviewPayload { }
