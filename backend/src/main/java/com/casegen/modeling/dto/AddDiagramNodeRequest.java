package com.casegen.modeling.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

public record AddDiagramNodeRequest(
        @NotNull UUID umlElementId,
        double x,
        double y,
        @Positive double width,
        @Positive double height,
        Integer zIndex
) {
}
