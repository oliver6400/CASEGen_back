package com.casegen.modeling.command;

import java.util.UUID;

public record UpdateUmlAssociationCommand(
        String name,
        String documentation,
        UUID packageId,
        UmlAssociationEndCommand firstEnd,
        UmlAssociationEndCommand secondEnd
) {
}
