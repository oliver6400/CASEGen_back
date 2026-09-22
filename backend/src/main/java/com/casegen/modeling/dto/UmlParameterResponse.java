package com.casegen.modeling.dto;

import com.casegen.modeling.domain.ParameterDirection;
import com.casegen.modeling.domain.PrimitiveType;
import com.casegen.modeling.domain.TypeKind;
import com.casegen.modeling.domain.UmlElementType;
import java.time.Instant;
import java.util.UUID;

public record UmlParameterResponse(
        UUID id,
        UUID modelId,
        UmlElementType elementType,
        String name,
        String documentation,
        UUID operationId,
        ParameterDirection direction,
        TypeKind typeKind,
        PrimitiveType primitiveType,
        UUID typeClassifierId,
        int lowerBound,
        Integer upperBound,
        int position,
        long modelRevision,
        Instant createdAt,
        Instant updatedAt
) {
}
