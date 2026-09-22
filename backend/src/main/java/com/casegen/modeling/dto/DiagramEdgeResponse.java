package com.casegen.modeling.dto;

import com.casegen.modeling.domain.DiagramSide;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DiagramEdgeResponse(
        UUID id,
        UUID umlElementId,
        UUID sourceNodeId,
        UUID targetNodeId,
        DiagramSide sourceSide,
        DiagramSide targetSide,
        int sourceAnchorIndex,
        int targetAnchorIndex,
        List<DiagramWaypoint> waypoints,
        Instant createdAt,
        Instant updatedAt
) {
}
