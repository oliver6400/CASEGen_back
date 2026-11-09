package com.casegen.interoperability.image;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.UUID;

public record ImageImportReconciliationPreviewRequest(
        @NotNull UUID targetModelId,
        @NotNull UUID targetDiagramId,
        UUID targetPackageId,
        @PositiveOrZero long expectedModelRevision,
        @PositiveOrZero long expectedDiagramRevision,
        @NotNull @Valid DetectedClassDiagram proposal) {

    public ImageImportReconciliationPreviewRequest(UUID targetModelId, UUID targetDiagramId,
            long expectedModelRevision, long expectedDiagramRevision, DetectedClassDiagram proposal) {
        this(targetModelId, targetDiagramId, null, expectedModelRevision, expectedDiagramRevision, proposal);
    }
}
