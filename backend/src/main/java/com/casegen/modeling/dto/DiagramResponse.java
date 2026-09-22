package com.casegen.modeling.dto;

import com.casegen.modeling.domain.DiagramType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DiagramResponse(
        UUID id,
        UUID modelId,
        UUID ownerPackageId,
        String name,
        DiagramType type,
        long revision,
        long lockVersion,
        UUID createdBy,
        Instant createdAt,
        Instant updatedAt,
        List<DiagramNodeResponse> nodes,
        List<DiagramEdgeResponse> edges
) {
}
