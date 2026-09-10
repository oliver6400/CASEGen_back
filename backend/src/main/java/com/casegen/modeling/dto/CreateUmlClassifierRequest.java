package com.casegen.modeling.dto;

import com.casegen.modeling.domain.ClassifierKind;
import com.casegen.modeling.domain.Visibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateUmlClassifierRequest(
        @NotBlank @Size(max = 180) String name,
        String documentation,
        UUID packageId,
        @NotNull ClassifierKind kind,
        Visibility visibility,
        boolean isAbstract,
        boolean isActive
) {
}
