package com.casegen.modeling.command;

import com.casegen.modeling.domain.ClassifierKind;
import com.casegen.modeling.domain.Visibility;
import java.util.UUID;

public record CreateUmlClassifierCommand(
        String name,
        String documentation,
        UUID packageId,
        ClassifierKind kind,
        Visibility visibility,
        boolean abstractClassifier,
        boolean active
) {
}
