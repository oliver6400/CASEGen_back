package com.casegen.admin.dto;

import java.time.Instant;
import java.util.UUID;

public record AdminProjectResponse(
        UUID id, String name, String description, Instant createdAt, Instant updatedAt
) {
}
