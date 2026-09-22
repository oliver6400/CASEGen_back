package com.casegen.validation.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ValidationResponse(UUID id, UUID modelId, long modelRevision, String status,
        boolean current, Instant startedAt, Instant finishedAt, List<ValidationIssueResponse> issues) {}
