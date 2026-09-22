package com.casegen.modeling.dto;

import com.casegen.modeling.domain.Visibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateUmlOperationRequest(
        @NotBlank @Size(max = 180) String name,
        String documentation,
        Visibility visibility,
        Boolean isAbstract,
        Boolean isStatic,
        Boolean isQuery
) {
}
