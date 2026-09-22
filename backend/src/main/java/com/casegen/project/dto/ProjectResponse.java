package com.casegen.project.dto;

import java.time.Instant;
import java.util.UUID;
import com.casegen.project.domain.ProjectVisibility;

public record ProjectResponse(
        UUID id,
        String name,
        String description,
        Instant createdAt,
        Instant updatedAt,
        ProjectVisibility visibility
) {
    public ProjectResponse(UUID id, String name, String description, Instant createdAt, Instant updatedAt) {
        this(id,name,description,createdAt,updatedAt,ProjectVisibility.PRIVATE);
    }
}
