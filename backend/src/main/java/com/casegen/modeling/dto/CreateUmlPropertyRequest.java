package com.casegen.modeling.dto;

import com.casegen.modeling.domain.PrimitiveType;
import com.casegen.modeling.domain.TypeKind;
import com.casegen.modeling.domain.Visibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateUmlPropertyRequest(
        @NotBlank @Size(max = 180) String name,
        String documentation,
        Visibility visibility,
        @NotNull TypeKind typeKind,
        PrimitiveType primitiveType,
        UUID typeClassifierId,
        @PositiveOrZero Integer lowerBound,
        @PositiveOrZero Integer upperBound,
        Boolean isStatic,
        Boolean isReadOnly,
        Boolean isDerived
) {
}
