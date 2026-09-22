package com.casegen.generation.dto;

import java.util.UUID;
import tools.jackson.databind.JsonNode;

public record GenerationProfileResponse(UUID id, UUID modelId, String name, String target, String groupId,
        String artifactId, String basePackage, int javaVersion, String springBootVersion, String database, JsonNode configJson) {}
