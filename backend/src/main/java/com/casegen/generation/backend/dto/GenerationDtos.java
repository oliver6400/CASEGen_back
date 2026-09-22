package com.casegen.generation.backend.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class GenerationDtos {
    private GenerationDtos() {}
    public record StartResponse(UUID generationId, String status) {}
    public record TestResult(UUID id,String testType,String status,String outputLog,Instant startedAt,Instant finishedAt) {}
    public record Artifact(UUID id,String artifactType,String fileName,String checksum,long sizeBytes,Instant createdAt) {}
    public record Response(UUID id,UUID logicalModelId,UUID modelVersionId,long modelVersionNumber,
            UUID generationProfileId,String generationProfileName,UUID requestedBy,
            String generatorVersion,String status,String currentStage,String errorMessage,Instant startedAt,Instant finishedAt,
            Instant createdAt,List<TestResult> tests,List<Artifact> artifacts) {}
}
