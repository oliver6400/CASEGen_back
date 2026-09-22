package com.casegen.modeling.dto;

import com.casegen.modeling.domain.DiagramSide;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;
import java.util.UUID;

public record AddDiagramEdgeRequest(
        @NotNull UUID umlElementId,
        @NotNull UUID sourceNodeId,
        @NotNull UUID targetNodeId,
        DiagramSide sourceSide,
        DiagramSide targetSide,
        @PositiveOrZero Integer sourceAnchorIndex,
        @PositiveOrZero Integer targetAnchorIndex,
        List<@Valid DiagramWaypoint> waypoints
) {
    public AddDiagramEdgeRequest(UUID umlElementId, UUID sourceNodeId, UUID targetNodeId) {
        this(umlElementId, sourceNodeId, targetNodeId,
                DiagramSide.RIGHT, DiagramSide.LEFT, 0, 0, List.of());
    }
}
