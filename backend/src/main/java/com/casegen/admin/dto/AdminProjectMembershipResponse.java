package com.casegen.admin.dto;

import com.casegen.project.domain.ProjectMemberRole;
import com.casegen.project.domain.ProjectMemberStatus;
import java.time.Instant;
import java.util.UUID;

public record AdminProjectMembershipResponse(
        UUID id,
        UUID projectId,
        String projectName,
        UUID userId,
        String userEmail,
        ProjectMemberRole role,
        ProjectMemberStatus status,
        Instant joinedAt
) {
}
