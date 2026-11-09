package com.casegen.interoperability.image;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;
import java.util.UUID;

public record ImageImportExistingConfirmRequest(
        @NotNull UUID targetModelId,
        @NotNull UUID targetDiagramId,
        UUID targetPackageId,
        @PositiveOrZero long expectedModelRevision,
        @PositiveOrZero long expectedDiagramRevision,
        @NotNull @Valid DetectedClassDiagram proposal,
        @NotEmpty List<@Valid Decision> decisions) {

    public ImageImportExistingConfirmRequest {
        decisions = decisions == null ? List.of() : List.copyOf(decisions);
    }

    public record Decision(
            @NotNull String temporaryId,
            @NotNull ImageImportReconciliationPreviewResponse.Action action,
            UUID candidateId) {
    }
}
