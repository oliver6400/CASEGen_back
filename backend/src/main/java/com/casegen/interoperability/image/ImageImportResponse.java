package com.casegen.interoperability.image;

import java.util.List;
import java.util.UUID;

public record ImageImportResponse(
        UUID modelId,
        String modelName,
        int classCount,
        int interfaceCount,
        int attributeCount,
        int operationCount,
        int relationshipCount,
        DiagramResult diagram,
        ValidationResult validation,
        List<DetectedClassDiagram.DetectionWarning> warnings) {
    public record DiagramResult(UUID diagramId, String name, int nodeCount, int edgeCount) {}
    public record ValidationResult(UUID validationId, String status, int issueCount) {}
}
