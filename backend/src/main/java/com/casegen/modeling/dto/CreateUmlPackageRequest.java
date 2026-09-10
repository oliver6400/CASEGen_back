package com.casegen.modeling.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateUmlPackageRequest(
        @NotBlank @Size(max = 180) String name,
        String documentation,
        UUID parentPackageId
) {
}
