package com.casegen.modeling.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UmlAssociationResponse(
        UUID id,
        UUID modelId,
        String name,
        String documentation,
        UUID packageId,
        List<UmlAssociationEndResponse> ends,
        long modelRevision,
        Instant createdAt,
        Instant updatedAt
) {
}
