package com.casegen.interoperability.image;

import java.util.Map;
import java.util.UUID;

public record ImageImportExistingConfirmResponse(
        UUID projectId,
        UUID modelId,
        UUID diagramId,
        long previousModelRevision,
        long modelRevision,
        long previousDiagramRevision,
        long diagramRevision,
        Map<String, UUID> createdIds,
        Map<String, UUID> reusedIds,
        int createdElementCount,
        int reusedElementCount,
        int skippedElementCount,
        int createdNodeCount,
        int reusedNodeCount,
        int createdEdgeCount,
        int reusedEdgeCount) {

    public ImageImportExistingConfirmResponse {
        createdIds = Map.copyOf(createdIds);
        reusedIds = Map.copyOf(reusedIds);
    }
}
