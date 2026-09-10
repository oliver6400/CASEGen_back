package com.casegen.modeling.dto;

import com.casegen.modeling.domain.UmlModelStatus;
import java.time.Instant;
import java.util.UUID;

public record UmlModelResponse(
        UUID id,
        UUID projectId,
        String name,
        String documentation,
        String umlVersion,
        long revision,
        long lockVersion,
        UmlModelStatus status,
        UUID createdBy,
        Instant createdAt,
        Instant updatedAt
) {
}
