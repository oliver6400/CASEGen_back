package com.casegen.generation.dto;

import jakarta.validation.constraints.*;
import tools.jackson.databind.JsonNode;

public record GenerationProfileRequest(
        @NotBlank @Size(max=180) String name,
        @NotNull Target target,
        @NotBlank @Size(max=180) @Pattern(regexp="[A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)*") String groupId,
        @NotBlank @Size(max=180) @Pattern(regexp="[A-Za-z0-9][A-Za-z0-9._-]*") String artifactId,
        @NotBlank @Size(max=180) @Pattern(regexp="[A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)*") String basePackage,
        @NotNull @Min(21) Integer javaVersion,
        @NotBlank @Size(max=40) @Pattern(regexp="\\d+\\.\\d+\\.\\d+(?:[-.][A-Za-z0-9.-]+)?") String springBootVersion,
        @NotNull Database database, JsonNode configJson) {
    public enum Target { SPRING_BOOT }
    public enum Database { POSTGRESQL }
}
