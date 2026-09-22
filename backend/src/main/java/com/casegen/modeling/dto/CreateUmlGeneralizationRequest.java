package com.casegen.modeling.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateUmlGeneralizationRequest(
        @NotNull UUID specificClassifierId,
        @NotNull UUID generalClassifierId,
        Boolean isSubstitutable,
        String documentation
) {
}
