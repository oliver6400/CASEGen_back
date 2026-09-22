package com.casegen.modeling.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateUmlRealizationRequest(
        @NotNull UUID clientClassifierId,
        @NotNull UUID supplierClassifierId,
        String documentation
) {
}
