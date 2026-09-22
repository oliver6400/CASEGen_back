package com.casegen.project.service;

import com.casegen.auth.domain.SystemRole;
import com.casegen.project.domain.ProjectMemberRole;

public record ProjectAccessDecision(
        SystemRole systemRole,
        ProjectMemberRole projectRole,
        boolean canView,
        boolean canEdit,
        boolean canManageMembers,
        boolean canGenerate
) {
}
