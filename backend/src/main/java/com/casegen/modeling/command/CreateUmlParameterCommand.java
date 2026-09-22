package com.casegen.modeling.command;

import com.casegen.modeling.domain.ParameterDirection;
import com.casegen.modeling.domain.PrimitiveType;
import com.casegen.modeling.domain.TypeKind;
import java.util.UUID;

public record CreateUmlParameterCommand(
        String name,
        String documentation,
        ParameterDirection direction,
        TypeKind typeKind,
        PrimitiveType primitiveType,
        UUID typeClassifierId,
        Integer lowerBound,
        Integer upperBound,
        Integer position
) {
}
