package com.casegen.modeling.dto;

import java.time.Instant;
import java.util.UUID;

public record DiagramNodeResponse(
        UUID id,
        UUID umlElementId,
        double x,
        double y,
        double width,
        double height,
        int zIndex,
        Instant createdAt,
        Instant updatedAt
) {
}
