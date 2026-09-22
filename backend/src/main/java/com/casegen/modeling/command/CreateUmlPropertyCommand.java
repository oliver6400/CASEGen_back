package com.casegen.modeling.command;

import com.casegen.modeling.domain.PrimitiveType;
import com.casegen.modeling.domain.TypeKind;
import com.casegen.modeling.domain.Visibility;
import java.util.UUID;

public record CreateUmlPropertyCommand(
        String name,
        String documentation,
        Visibility visibility,
        TypeKind typeKind,
        PrimitiveType primitiveType,
        UUID typeClassifierId,
        Integer lowerBound,
        Integer upperBound,
        boolean staticProperty,
        boolean readOnly,
        boolean derived
) {
}
