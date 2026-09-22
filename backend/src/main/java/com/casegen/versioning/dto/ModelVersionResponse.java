package com.casegen.versioning.dto;

import java.time.Instant;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

public record ModelVersionResponse(UUID id, UUID modelId, UUID validationRunId, long versionNumber, UUID createdBy,
        JsonNode semanticSnapshot, JsonNode diagramSnapshot, String checksum, Instant createdAt) {}
