package com.casegen.project.service;

import com.casegen.auth.domain.SystemRole;
import com.casegen.auth.domain.User;
import com.casegen.project.domain.Project;
import com.casegen.project.domain.ProjectMember;
import com.casegen.project.domain.ProjectMemberRole;
import com.casegen.project.domain.ProjectMemberStatus;
import com.casegen.project.dto.CreateProjectRequest;
import com.casegen.project.dto.ProjectResponse;
import com.casegen.project.dto.ProjectAccessResponse;
import com.casegen.project.dto.UpdateProjectRequest;
import com.casegen.project.repository.ProjectRepository;
import com.casegen.project.repository.ProjectMemberRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectAuthorizationService authorizationService;

    public ProjectService(
            ProjectRepository projectRepository,
            ProjectMemberRepository projectMemberRepository,
            ProjectAuthorizationService authorizationService) {
        this.projectRepository = projectRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.authorizationService = authorizationService;
    }

    @Transactional
    public ProjectResponse create(CreateProjectRequest request) {
        User owner = authorizationService.currentUser();
        Project project = new Project(request.name(), request.description());
        projectRepository.save(project);
        projectMemberRepository.save(new ProjectMember(project, owner, ProjectMemberRole.OWNER));
        return toResponse(project);
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> findAll() {
        User user = authorizationService.currentUser();
        List<Project> projects = user.getSystemRole() == SystemRole.SUPERUSER
                ? projectRepository.findAll()
                : projectRepository.findVisibleTo(user.getId());
        return projects.stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProjectResponse findById(UUID id) {
        authorizationService.requireRead(id);
        return toResponse(findProject(id));
    }

    @Transactional(readOnly = true)
    public ProjectAccessResponse getAccess(UUID id) {
        ProjectAccessDecision access = authorizationService.getAccess(id);
        return new ProjectAccessResponse(
                access.systemRole(),
                access.projectRole(),
                access.canView(),
                access.canEdit(),
                access.canManageMembers(),
                access.canGenerate());
    }

    @Transactional
    public ProjectResponse update(UUID id, UpdateProjectRequest request) {
        authorizationService.requireManageMembers(id);
        Project project = findProject(id);
        project.update(request.name(), request.description());
        return toResponse(projectRepository.save(project));
    }

    private Project findProject(UUID id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
    }

    private ProjectResponse toResponse(Project project) {
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.getCreatedAt(),
                project.getUpdatedAt(), project.getVisibility()
        );
    }
}
