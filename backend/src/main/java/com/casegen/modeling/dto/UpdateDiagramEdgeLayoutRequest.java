package com.casegen.modeling.dto;

import com.casegen.modeling.domain.DiagramSide;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;

public record UpdateDiagramEdgeLayoutRequest(
        @NotNull DiagramSide sourceSide,
        @NotNull DiagramSide targetSide,
        @PositiveOrZero int sourceAnchorIndex,
        @PositiveOrZero int targetAnchorIndex,
        @NotNull List<@Valid DiagramWaypoint> waypoints
) {
}
