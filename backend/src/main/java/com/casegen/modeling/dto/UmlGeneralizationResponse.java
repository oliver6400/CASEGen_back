package com.casegen.modeling.dto;

import java.time.Instant;
import java.util.UUID;

public record UmlGeneralizationResponse(
        UUID id,
        UUID modelId,
        UUID specificClassifierId,
        UUID generalClassifierId,
        boolean isSubstitutable,
        String documentation,
        long modelRevision,
        Instant createdAt,
        Instant updatedAt
) {
}
