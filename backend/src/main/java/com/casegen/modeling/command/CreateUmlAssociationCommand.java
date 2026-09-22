package com.casegen.modeling.command;

import java.util.UUID;

public record CreateUmlAssociationCommand(
        String name,
        String documentation,
        UUID packageId,
        UmlAssociationEndCommand firstEnd,
        UmlAssociationEndCommand secondEnd
) {
}
