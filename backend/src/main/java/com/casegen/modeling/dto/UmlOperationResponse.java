package com.casegen.modeling.dto;

import com.casegen.modeling.domain.UmlElementType;
import com.casegen.modeling.domain.Visibility;
import java.time.Instant;
import java.util.UUID;

public record UmlOperationResponse(
        UUID id,
        UUID modelId,
        UmlElementType elementType,
        String name,
        String documentation,
        UUID ownerClassifierId,
        Visibility visibility,
        boolean isAbstract,
        boolean isStatic,
        boolean isQuery,
        long modelRevision,
        Instant createdAt,
        Instant updatedAt
) {
}
