package com.casegen.modeling.command;

import java.util.UUID;

public record CreateUmlPackageCommand(
        String name,
        String documentation,
        UUID parentPackageId
) {
}
