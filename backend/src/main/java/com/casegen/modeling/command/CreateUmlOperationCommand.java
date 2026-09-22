package com.casegen.modeling.command;

import com.casegen.modeling.domain.Visibility;

public record CreateUmlOperationCommand(
        String name,
        String documentation,
        Visibility visibility,
        boolean abstractOperation,
        boolean staticOperation,
        boolean query
) {
}
