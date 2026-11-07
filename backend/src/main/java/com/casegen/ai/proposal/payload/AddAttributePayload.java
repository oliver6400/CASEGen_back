package com.casegen.ai.proposal.payload;

import com.casegen.modeling.domain.PrimitiveType;
import com.casegen.modeling.domain.TypeKind;
import com.casegen.modeling.domain.Visibility;
import java.util.UUID;

public record AddAttributePayload(UUID ownerClassifierId, String name, Visibility visibility,
        TypeKind typeKind, PrimitiveType primitiveType, UUID typeClassifierId,
        Integer lowerBound, Integer upperBound, Boolean staticProperty,
        Boolean readOnly, Boolean derived) implements AiProposalPayload {
}
