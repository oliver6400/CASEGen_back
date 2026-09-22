package com.casegen.modeling.dto;

import com.casegen.modeling.domain.ParameterDirection;
import com.casegen.modeling.domain.PrimitiveType;
import com.casegen.modeling.domain.TypeKind;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateUmlParameterRequest(
        @Size(max = 180) String name,
        String documentation,
        @NotNull ParameterDirection direction,
        @NotNull TypeKind typeKind,
        PrimitiveType primitiveType,
        UUID typeClassifierId,
        @PositiveOrZero Integer lowerBound,
        @PositiveOrZero Integer upperBound,
        @NotNull @PositiveOrZero Integer position
) {
}
