package com.casegen.modeling.dto;

import com.casegen.modeling.domain.PrimitiveType;
import com.casegen.modeling.domain.TypeKind;
import com.casegen.modeling.domain.UmlElementType;
import com.casegen.modeling.domain.Visibility;
import java.time.Instant;
import java.util.UUID;

public record UmlPropertyResponse(
        UUID id,
        UUID modelId,
        UmlElementType elementType,
        String name,
        String documentation,
        UUID ownerClassifierId,
        Visibility visibility,
        TypeKind typeKind,
        PrimitiveType primitiveType,
        UUID typeClassifierId,
        int lowerBound,
        Integer upperBound,
        boolean isStatic,
        boolean isReadOnly,
        boolean isDerived,
        long modelRevision,
        Instant createdAt,
        Instant updatedAt
) {
}
