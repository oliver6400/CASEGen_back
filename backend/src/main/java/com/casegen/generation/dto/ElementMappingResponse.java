package com.casegen.generation.dto;

import java.util.UUID;
import tools.jackson.databind.JsonNode;

public record ElementMappingResponse(UUID id, UUID generationProfileId, UUID umlElementId, String targetKind, JsonNode configJson) {}
