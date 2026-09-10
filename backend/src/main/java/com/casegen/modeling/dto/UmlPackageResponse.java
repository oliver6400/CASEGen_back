package com.casegen.modeling.dto;

import com.casegen.modeling.domain.UmlElementType;
import java.time.Instant;
import java.util.UUID;

public record UmlPackageResponse(
        UUID id,
        UUID modelId,
        UmlElementType elementType,
        String name,
        String documentation,
        UUID parentPackageId,
        long modelRevision,
        Instant createdAt,
        Instant updatedAt
) {
}
