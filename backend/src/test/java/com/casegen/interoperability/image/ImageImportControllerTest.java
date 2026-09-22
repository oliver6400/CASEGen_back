package com.casegen.interoperability.image;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ImageImportControllerTest {
    private ImageImportService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(ImageImportService.class);
        mvc = MockMvcBuilders.standaloneSetup(new ImageImportController(service))
                .setControllerAdvice(new ImageImportExceptionHandler()).build();
    }

    @Test
    void analysisReturnsProposalWithoutCreatingModel() throws Exception {
        UUID projectId = UUID.randomUUID();
        DetectedClassDiagram proposal = proposal();
        when(service.analyze(eq(projectId), any(byte[].class), eq("image/png"), eq("diagram.png")))
                .thenReturn(proposal);
        byte[] png = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};

        mvc.perform(multipart("/api/projects/{projectId}/image-import/analyze", projectId)
                        .file(new MockMultipartFile("file", "diagram.png", "image/png", png)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.detectedClassifiers[0].name").value("Cliente"))
                .andExpect(jsonPath("$.confidence").value(0.92));
    }

    @Test
    void confirmCreatesModelOnlyAfterExplicitJsonRequest() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID modelId = UUID.randomUUID();
        UUID diagramId = UUID.randomUUID();
        when(service.confirm(eq(projectId), any())).thenReturn(new ImageImportResponse(
                modelId, "Ventas", 2, 0, 4, 0, 1,
                new ImageImportResponse.DiagramResult(diagramId, "Ventas - Importado", 2, 1),
                new ImageImportResponse.ValidationResult(UUID.randomUUID(), "VALID", 0), List.of()));

        mvc.perform(post("/api/projects/{projectId}/image-import/confirm", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"suggestedModelName":"Ventas","detectedClassifiers":[],
                                "detectedAttributes":[],"detectedOperations":[],
                                "detectedRelationships":[],"warnings":[],"confidence":0.9}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/uml-models/" + modelId))
                .andExpect(jsonPath("$.validation.status").value("VALID"))
                .andExpect(jsonPath("$.diagram.diagramId").value(diagramId.toString()));
    }

    @Test
    void statusDoesNotExposeApiKey() throws Exception {
        when(service.configuration()).thenReturn(
                new ImageImportConfigurationResponse(true, "LOCAL", "opencv+tesseract", false));

        mvc.perform(get("/api/image-import/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.provider").value("LOCAL"))
                .andExpect(jsonPath("$.model").value("opencv+tesseract"))
                .andExpect(jsonPath("$.localOcrAvailable").value(false))
                .andExpect(jsonPath("$.fallbackLocalAvailable").value(false))
                .andExpect(jsonPath("$.apiKey").doesNotExist());
    }

    private DetectedClassDiagram proposal() {
        return new DetectedClassDiagram("Ventas", List.of(
                new DetectedClassDiagram.DetectedClassifier("c1", "Cliente", "CLASS", "PUBLIC", 0.95)),
                List.of(), List.of(), List.of(), List.of(), 0.92);
    }
}
