package com.casegen.modeling.dto;

import java.time.Instant;
import java.util.UUID;

public record UmlRealizationResponse(
        UUID id,
        UUID modelId,
        UUID clientClassifierId,
        UUID supplierClassifierId,
        String documentation,
        long modelRevision,
        Instant createdAt,
        Instant updatedAt
) {
}
