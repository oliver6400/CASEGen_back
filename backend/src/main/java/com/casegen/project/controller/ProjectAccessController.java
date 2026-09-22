package com.casegen.project.controller;

import com.casegen.project.dto.ProjectAccessResponse;
import com.casegen.project.service.ProjectService;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProjectAccessController {

    private final ProjectService projectService;

    public ProjectAccessController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping("/api/projects/{projectId}/access")
    public ProjectAccessResponse getAccess(@PathVariable UUID projectId) {
        return projectService.getAccess(projectId);
    }
}
