package com.casegen.modeling.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateUmlModelRequest(
        @NotBlank @Size(max = 180) String name,
        String documentation
) {
}
