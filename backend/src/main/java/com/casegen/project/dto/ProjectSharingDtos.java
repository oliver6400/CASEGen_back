package com.casegen.project.dto;

import com.casegen.project.domain.*;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;

public final class ProjectSharingDtos {
    private ProjectSharingDtos() {}
    public record VisibilityRequest(@NotNull ProjectVisibility visibility) {}
    public record VisibilityResponse(UUID projectId, ProjectVisibility visibility) {}
    public record InviteRequest(@NotBlank @Email @Size(max=254) String email, @NotNull ProjectInvitationRole role) {}
    public record MemberRoleRequest(@NotNull ProjectInvitationRole role) {}
    public record MemberResponse(UUID id, UUID userId, String displayName, String email, ProjectMemberRole role, ProjectMemberStatus status) {}
    public record InvitationResponse(UUID id, UUID projectId, String projectName, String email, ProjectInvitationRole role,
            ProjectInvitationStatus status, Instant expiresAt, Instant createdAt) {}
}
