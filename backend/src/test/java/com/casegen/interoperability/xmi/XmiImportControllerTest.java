package com.casegen.interoperability.xmi;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class XmiImportControllerTest {
    private XmiImportService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(XmiImportService.class);
        mvc = MockMvcBuilders.standaloneSetup(new XmiImportController(service))
                .setControllerAdvice(new XmiImportExceptionHandler()).build();
    }

    @Test
    void importsMultipartXmiAndReturnsCreatedModelLocation() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID modelId = UUID.randomUUID();
        UUID diagramId = UUID.randomUUID();
        UUID validationId = UUID.randomUUID();
        when(service.importModel(eq(projectId), any(byte[].class))).thenReturn(new XmiImportResponse(
                modelId, "Sales", XmiDialect.CASEGEN_UML_2_5_1, 1, 2, 1, 3, 1, 2,
                1, 0, 1, new XmiImportResponse.DiagramSummary(
                        diagramId, "Sales - Importado", 3, 2),
                List.of(), new XmiImportResponse.ValidationSummary(
                        validationId, "VALID", 0, List.of())));
        var file = new MockMultipartFile("file", "sales.xmi", "application/xml",
                "<xmi:XMI/>".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        mvc.perform(multipart("/api/projects/{projectId}/xmi/import", projectId).file(file))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/uml-models/" + modelId))
                .andExpect(jsonPath("$.modelId").value(modelId.toString()))
                .andExpect(jsonPath("$.diagram.diagramId").value(diagramId.toString()))
                .andExpect(jsonPath("$.diagram.name").value("Sales - Importado"))
                .andExpect(jsonPath("$.validation.status").value("VALID"));
    }
}
