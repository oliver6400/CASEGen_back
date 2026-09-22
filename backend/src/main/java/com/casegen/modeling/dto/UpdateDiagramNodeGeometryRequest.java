package com.casegen.modeling.dto;

import jakarta.validation.constraints.Positive;

public record UpdateDiagramNodeGeometryRequest(
        double x,
        double y,
        @Positive double width,
        @Positive double height,
        Integer zIndex
) {
}
