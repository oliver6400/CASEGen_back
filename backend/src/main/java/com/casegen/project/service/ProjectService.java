package com.casegen.project.service;

import com.casegen.project.domain.Project;
import com.casegen.project.dto.CreateProjectRequest;
import com.casegen.project.dto.ProjectResponse;
import com.casegen.project.dto.UpdateProjectRequest;
import com.casegen.project.repository.ProjectRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;

    public ProjectService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    public ProjectResponse create(CreateProjectRequest request) {
        Project project = new Project(request.name(), request.description());
        return toResponse(projectRepository.save(project));
    }

    public List<ProjectResponse> findAll() {
        return projectRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    public ProjectResponse findById(UUID id) {
        return toResponse(findProject(id));
    }

    public ProjectResponse update(UUID id, UpdateProjectRequest request) {
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
                project.getUpdatedAt()
        );
    }
}
