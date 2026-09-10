package com.casegen.project.service;

import com.casegen.auth.domain.SystemRole;
import com.casegen.auth.domain.User;
import com.casegen.auth.service.CurrentUserService;
import com.casegen.project.domain.ProjectMember;
import com.casegen.project.domain.ProjectMemberRole;
import com.casegen.project.domain.ProjectMemberStatus;
import com.casegen.project.repository.ProjectMemberRepository;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class ProjectAuthorizationService {

    private static final Set<ProjectMemberRole> WRITERS =
            EnumSet.of(ProjectMemberRole.OWNER, ProjectMemberRole.COLLABORATOR);

    private final CurrentUserService currentUserService;
    private final ProjectMemberRepository projectMemberRepository;

    public ProjectAuthorizationService(
            CurrentUserService currentUserService,
            ProjectMemberRepository projectMemberRepository) {
        this.currentUserService = currentUserService;
        this.projectMemberRepository = projectMemberRepository;
    }

    public User currentUser() {
        return currentUserService.requireCurrentUser();
    }

    public User requireRead(UUID projectId) {
        return requireRoles(projectId, EnumSet.allOf(ProjectMemberRole.class));
    }

    public User requireWrite(UUID projectId) {
        return requireRoles(projectId, WRITERS);
    }

    public User requireManageMembers(UUID projectId) {
        return requireRoles(projectId, EnumSet.of(ProjectMemberRole.OWNER));
    }

    public User requireGenerate(UUID projectId) {
        return requireWrite(projectId);
    }

    private User requireRoles(UUID projectId, Set<ProjectMemberRole> allowedRoles) {
        User user = currentUser();
        if (user.getSystemRole() == SystemRole.SUPERUSER) {
            return user;
        }

        ProjectMember membership = projectMemberRepository
                .findByProjectIdAndUserIdAndStatus(projectId, user.getId(), ProjectMemberStatus.ACTIVE)
                .orElseThrow(this::denied);
        if (!allowedRoles.contains(membership.getRole())) {
            throw denied();
        }
        return user;
    }

    private AccessDeniedException denied() {
        return new AccessDeniedException("The user has no permission for this project operation");
    }
}
