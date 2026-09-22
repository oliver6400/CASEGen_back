package com.casegen.modeling.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record UpdateUmlAssociationRequest(
        @Size(max = 180) String name,
        String documentation,
        UUID packageId,
        @Valid @NotNull UmlAssociationEndRequest firstEnd,
        @Valid @NotNull UmlAssociationEndRequest secondEnd
) {
}
