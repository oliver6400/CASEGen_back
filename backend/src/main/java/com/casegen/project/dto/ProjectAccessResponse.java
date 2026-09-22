package com.casegen.project.dto;

import com.casegen.auth.domain.SystemRole;
import com.casegen.project.domain.ProjectMemberRole;

public record ProjectAccessResponse(
        SystemRole systemRole,
        ProjectMemberRole projectRole,
        boolean canView,
        boolean canEdit,
        boolean canManageMembers,
        boolean canGenerate
) {
}
