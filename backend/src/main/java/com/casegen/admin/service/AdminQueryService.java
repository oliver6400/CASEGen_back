package com.casegen.admin.service;

import com.casegen.admin.dto.AdminProjectMembershipResponse;
import com.casegen.admin.dto.AdminProjectResponse;
import com.casegen.admin.dto.AdminUserResponse;
import com.casegen.auth.domain.SystemRole;
import com.casegen.auth.repository.UserRepository;
import com.casegen.auth.service.CurrentUserService;
import com.casegen.project.repository.ProjectMemberRepository;
import com.casegen.project.repository.ProjectRepository;
import java.util.Comparator;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminQueryService {

    private final CurrentUserService currentUserService;
    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;

    public AdminQueryService(
            CurrentUserService currentUserService,
            UserRepository userRepository,
            ProjectRepository projectRepository,
            ProjectMemberRepository projectMemberRepository) {
        this.currentUserService = currentUserService;
        this.userRepository = userRepository;
        this.projectRepository = projectRepository;
        this.projectMemberRepository = projectMemberRepository;
    }

    @Transactional(readOnly = true)
    public List<AdminUserResponse> listUsers() {
        requireSuperuser();
        return userRepository.findAll().stream()
                .sorted(Comparator.comparing(user -> user.getEmail().toLowerCase()))
                .map(user -> new AdminUserResponse(
                        user.getId(), user.getEmail(), user.getDisplayName(), user.getSystemRole(),
                        user.isEnabled(), user.getCreatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AdminProjectResponse> listProjects() {
        requireSuperuser();
        return projectRepository.findAll().stream()
                .sorted(Comparator.comparing(project -> project.getName().toLowerCase()))
                .map(project -> new AdminProjectResponse(
                        project.getId(), project.getName(), project.getDescription(),
                        project.getCreatedAt(), project.getUpdatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AdminProjectMembershipResponse> listProjectMemberships() {
        requireSuperuser();
        return projectMemberRepository.findAll().stream()
                .sorted(Comparator.comparing(member -> member.getProject().getName().toLowerCase()))
                .map(member -> new AdminProjectMembershipResponse(
                        member.getId(), member.getProject().getId(), member.getProject().getName(),
                        member.getUser().getId(), member.getUser().getEmail(), member.getRole(),
                        member.getStatus(), member.getJoinedAt()))
                .toList();
    }

    private void requireSuperuser() {
        if (currentUserService.requireCurrentUser().getSystemRole() != SystemRole.SUPERUSER) {
            throw new AccessDeniedException("Only SUPERUSER can access administration queries");
        }
    }
}
