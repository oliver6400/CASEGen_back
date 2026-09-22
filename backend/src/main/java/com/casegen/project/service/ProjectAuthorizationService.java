package com.casegen.project.service;

import com.casegen.auth.domain.SystemRole;
import com.casegen.auth.domain.User;
import com.casegen.auth.repository.UserRepository;
import com.casegen.auth.service.CurrentUserService;
import com.casegen.project.domain.ProjectMember;
import com.casegen.project.domain.ProjectMemberRole;
import com.casegen.project.domain.ProjectMemberStatus;
import com.casegen.project.repository.ProjectMemberRepository;
import com.casegen.project.repository.ProjectRepository;
import com.casegen.project.domain.ProjectVisibility;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class ProjectAuthorizationService {

    private final CurrentUserService currentUserService;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;

    public ProjectAuthorizationService(
            CurrentUserService currentUserService,
            ProjectMemberRepository projectMemberRepository, ProjectRepository projectRepository,
            UserRepository userRepository) {
        this.currentUserService = currentUserService;
        this.projectMemberRepository = projectMemberRepository;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
    }

    public User currentUser() {
        return currentUserService.requireCurrentUser();
    }

    public User requireRead(UUID projectId) {
        ProjectAccessDecision access = getAccess(projectId);
        return require(access.canView());
    }

    public User requireWrite(UUID projectId) {
        ProjectAccessDecision access = getAccess(projectId);
        return require(access.canEdit());
    }

    public User requireManageMembers(UUID projectId) {
        ProjectAccessDecision access = getAccess(projectId);
        return require(access.canManageMembers());
    }

    public User requireGenerate(UUID projectId) {
        ProjectAccessDecision access = getAccess(projectId);
        return require(access.canGenerate());
    }

    public ProjectAccessDecision getAccess(UUID projectId) {
        User user = currentUser();
        if (user.getSystemRole() == SystemRole.SUPERUSER) {
            return new ProjectAccessDecision(SystemRole.SUPERUSER, null, true, true, true, true);
        }

        ProjectMember membership = projectMemberRepository
                .findByProjectIdAndUserIdAndStatus(projectId, user.getId(), ProjectMemberStatus.ACTIVE)
                .orElse(null);
        if (membership == null) {
            boolean publicProject = projectRepository.findById(projectId)
                    .map(p -> p.getVisibility() == ProjectVisibility.PUBLIC).orElse(false);
            if (!publicProject) throw denied();
            return new ProjectAccessDecision(SystemRole.USER, null, true, false, false, false);
        }
        ProjectMemberRole role = membership.getRole();
        boolean canEdit = role == ProjectMemberRole.OWNER || role == ProjectMemberRole.COLLABORATOR;
        boolean canManage = role == ProjectMemberRole.OWNER;
        return new ProjectAccessDecision(SystemRole.USER, role, true, canEdit, canManage, canEdit);
    }

    /** Read authorization for transports that carry an explicit principal, such as STOMP. */
    public boolean canReadAs(UUID projectId, UUID userId) {
        boolean publicProject = projectRepository.findById(projectId)
                .map(project -> project.getVisibility() == ProjectVisibility.PUBLIC)
                .orElse(false);
        if (userId == null) return publicProject;
        User user = userRepository.findById(userId).filter(User::isEnabled).orElse(null);
        if (user == null) return false;
        if (user.getSystemRole() == SystemRole.SUPERUSER) return true;
        return publicProject || projectMemberRepository
                .findByProjectIdAndUserIdAndStatus(projectId, userId, ProjectMemberStatus.ACTIVE)
                .isPresent();
    }

    public boolean canWriteAs(UUID projectId, UUID userId) {
        if (userId == null) return false;
        User user = userRepository.findById(userId).filter(User::isEnabled).orElse(null);
        if (user == null) return false;
        if (user.getSystemRole() == SystemRole.SUPERUSER) return true;
        return projectMemberRepository
                .findByProjectIdAndUserIdAndStatus(projectId, userId, ProjectMemberStatus.ACTIVE)
                .map(ProjectMember::getRole)
                .map(role -> role == ProjectMemberRole.OWNER || role == ProjectMemberRole.COLLABORATOR)
                .orElse(false);
    }

    private User require(boolean granted) {
        if (!granted) {
            throw denied();
        }
        return currentUser();
    }

    private AccessDeniedException denied() {
        return new AccessDeniedException("The user has no permission for this project operation");
    }
}
