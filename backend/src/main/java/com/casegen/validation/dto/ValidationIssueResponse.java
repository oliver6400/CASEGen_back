package com.casegen.validation.dto;

import java.util.UUID;

public record ValidationIssueResponse(UUID id, UUID umlElementId, String severity, String code, String message, String path) {}
