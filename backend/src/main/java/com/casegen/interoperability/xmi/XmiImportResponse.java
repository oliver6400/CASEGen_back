package com.casegen.interoperability.xmi;

import com.casegen.validation.dto.ValidationIssueResponse;
import java.util.List;
import java.util.UUID;

public record XmiImportResponse(
        UUID modelId,
        String modelName,
        XmiDialect dialect,
        int packageCount,
        int classCount,
        int interfaceCount,
        int propertyCount,
        int operationCount,
        int parameterCount,
        int associationCount,
        int generalizationCount,
        int realizationCount,
        DiagramSummary diagram,
        List<XmiImportData.XmiImportWarning> warnings,
        ValidationSummary validation) {

    public record DiagramSummary(UUID diagramId, String name, int nodeCount, int edgeCount) {}

    public record ValidationSummary(UUID validationId, String status,
            int issueCount, List<ValidationIssueResponse> issues) {}
}
