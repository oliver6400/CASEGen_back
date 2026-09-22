package com.casegen.generation.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

public record ElementMappingRequest(@NotNull UUID umlElementId, @NotNull TargetKind targetKind, JsonNode configJson) {
    public enum TargetKind { ENTITY, VALUE_OBJECT, SERVICE, JAVA_INTERFACE, IGNORE }
}
