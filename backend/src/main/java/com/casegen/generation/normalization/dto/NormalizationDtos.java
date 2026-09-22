package com.casegen.generation.normalization.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

public final class NormalizationDtos {
    private NormalizationDtos() {}

    public record FormAssessment(String normalForm, String status, String statement) {}

    public record IssueResponse(UUID id, UUID logicalTableId, String tableName,
            UUID logicalColumnId, String columnName, String normalForm, String severity,
            String code, String message, String recommendation, JsonNode metadata,
            String sourceElementId) {}

    public record Response(UUID id, UUID logicalModelId, String status,
            String highestNormalForm, UUID createdBy, Instant createdAt,
            List<FormAssessment> forms, List<IssueResponse> issues) {}
}
