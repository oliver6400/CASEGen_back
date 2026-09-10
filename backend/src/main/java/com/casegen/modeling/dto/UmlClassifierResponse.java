package com.casegen.modeling.dto;

import com.casegen.modeling.domain.ClassifierKind;
import com.casegen.modeling.domain.UmlElementType;
import com.casegen.modeling.domain.Visibility;
import java.time.Instant;
import java.util.UUID;

public record UmlClassifierResponse(
        UUID id,
        UUID modelId,
        UmlElementType elementType,
        String name,
        String documentation,
        UUID packageId,
        ClassifierKind kind,
        Visibility visibility,
        boolean isAbstract,
        boolean isActive,
        long modelRevision,
        Instant createdAt,
        Instant updatedAt
) {
}
