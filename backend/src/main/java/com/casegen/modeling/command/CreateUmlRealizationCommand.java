package com.casegen.modeling.command;

import java.util.UUID;

public record CreateUmlRealizationCommand(
        UUID clientClassifierId,
        UUID supplierClassifierId,
        String documentation
) {
}
