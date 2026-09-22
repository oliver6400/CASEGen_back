package com.casegen.modeling.command;

import java.util.UUID;

public record CreateUmlGeneralizationCommand(
        UUID specificClassifierId,
        UUID generalClassifierId,
        boolean substitutable,
        String documentation
) {
}
