package com.casegen.modeling.dto;

import com.casegen.modeling.domain.DiagramType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateDiagramRequest(
        @NotBlank @Size(max = 180) String name,
        @NotNull DiagramType type,
        UUID ownerPackageId
) {
}
