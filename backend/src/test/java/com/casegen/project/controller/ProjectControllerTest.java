package com.casegen.project.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.casegen.project.dto.CreateProjectRequest;
import com.casegen.project.dto.ProjectResponse;
import com.casegen.project.dto.UpdateProjectRequest;
import com.casegen.project.service.ProjectService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

class ProjectControllerTest {

    private ProjectService projectService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        projectService = org.mockito.Mockito.mock(ProjectService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ProjectController(projectService)).build();
    }

    @Test
    void createsProjectWith201Created() throws Exception {
        UUID id = UUID.randomUUID();
        Instant now = Instant.parse("2026-09-01T00:00:00Z");
        when(projectService.create(any(CreateProjectRequest.class)))
                .thenReturn(new ProjectResponse(id, "Ventas", "Sistema de ventas", now, now));

        mockMvc.perform(post("/api/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ventas\",\"description\":\"Sistema de ventas\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/projects/" + id))
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("Ventas"));

        verify(projectService).create(any(CreateProjectRequest.class));
    }

    @Test
    void returnsProjects() throws Exception {
        UUID id = UUID.randomUUID();
        Instant now = Instant.parse("2026-09-01T00:00:00Z");
        when(projectService.findAll()).thenReturn(List.of(new ProjectResponse(id, "Ventas", null, now, now)));

        mockMvc.perform(get("/api/v1/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id.toString()));
    }

    @Test
    void returns404WhenProjectDoesNotExist() throws Exception {
        UUID id = UUID.randomUUID();
        when(projectService.findById(id))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));

        mockMvc.perform(get("/api/v1/projects/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void updatesProject() throws Exception {
        UUID id = UUID.randomUUID();
        Instant now = Instant.parse("2026-09-01T00:00:00Z");
        when(projectService.update(any(UUID.class), any(UpdateProjectRequest.class)))
                .thenReturn(new ProjectResponse(id, "Ventas 2", null, now, now));

        mockMvc.perform(put("/api/v1/projects/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ventas 2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ventas 2"));
    }

    @Test
    void rejectsInvalidProject() throws Exception {
        mockMvc.perform(post("/api/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}
