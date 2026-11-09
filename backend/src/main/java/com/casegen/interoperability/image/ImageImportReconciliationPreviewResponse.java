package com.casegen.interoperability.image;

import java.util.List;
import java.util.UUID;

public record ImageImportReconciliationPreviewResponse(
        UUID projectId,
        UUID targetModelId,
        UUID targetDiagramId,
        UUID targetPackageId,
        long modelRevision,
        long diagramRevision,
        List<ReconciliationItem> items,
        List<ExistingDiagramNode> existingNodes) {

    public ImageImportReconciliationPreviewResponse(UUID projectId, UUID targetModelId,
            UUID targetDiagramId, long modelRevision, long diagramRevision,
            List<ReconciliationItem> items, List<ExistingDiagramNode> existingNodes) {
        this(projectId, targetModelId, targetDiagramId, null, modelRevision, diagramRevision,
                items, existingNodes);
    }

    public ImageImportReconciliationPreviewResponse {
        items = List.copyOf(items);
        existingNodes = List.copyOf(existingNodes);
    }

    public enum Action {
        CREATE,
        MAP_TO_EXISTING,
        SKIP,
        CONFLICT
    }

    public enum ElementKind {
        CLASSIFIER,
        ATTRIBUTE,
        OPERATION,
        RELATIONSHIP
    }

    public record ReconciliationItem(
            String temporaryId,
            ElementKind elementKind,
            String detectedName,
            Action suggestedAction,
            boolean decisionRequired,
            String reasonCode,
            String explanation,
            List<Candidate> candidates,
            List<String> dependsOnTemporaryIds) {

        public ReconciliationItem {
            candidates = List.copyOf(candidates);
            dependsOnTemporaryIds = List.copyOf(dependsOnTemporaryIds);
        }
    }

    public record Candidate(
            UUID elementId,
            String name,
            String classifierKind,
            UUID packageId,
            UUID diagramNodeId) {
    }

    public record ExistingDiagramNode(
            UUID nodeId,
            UUID umlElementId,
            double x,
            double y,
            double width,
            double height,
            int zIndex) {
    }
}
