package com.casegen.project.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.casegen.project.domain.Project;
import com.casegen.project.dto.CreateProjectRequest;
import com.casegen.project.dto.UpdateProjectRequest;
import com.casegen.project.repository.ProjectRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class ProjectServiceTest {

    private ProjectRepository projectRepository;
    private ProjectService projectService;

    @BeforeEach
    void setUp() {
        projectRepository = Mockito.mock(ProjectRepository.class);
        projectService = new ProjectService(projectRepository);
    }

    @Test
    void createsProject() {
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = projectService.create(new CreateProjectRequest("Ventas", "Sistema de ventas"));

        assertEquals("Ventas", response.name());
        assertEquals("Sistema de ventas", response.description());
        verify(projectRepository).save(any(Project.class));
    }

    @Test
    void updatesExistingProject() {
        UUID id = UUID.randomUUID();
        Project project = new Project("Ventas", null);
        when(projectRepository.findById(id)).thenReturn(Optional.of(project));
        when(projectRepository.save(project)).thenReturn(project);

        var response = projectService.update(id, new UpdateProjectRequest("Ventas 2", "Actualizado"));

        assertEquals("Ventas 2", response.name());
        assertEquals("Actualizado", response.description());
        verify(projectRepository).save(project);
    }

    @Test
    void rejectsMissingProject() {
        UUID id = UUID.randomUUID();
        when(projectRepository.findById(id)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> projectService.findById(id)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }
}
