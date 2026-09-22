package com.casegen.interoperability.xmi;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class XmiExportControllerTest {
    private XmiExportService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(XmiExportService.class);
        mvc = MockMvcBuilders.standaloneSetup(new XmiExportController(service)).build();
    }

    @Test
    void downloadsXmiUsingTheSuggestedModelName() throws Exception {
        UUID modelId = UUID.randomUUID();
        byte[] body = "<?xml version=\"1.0\"?><xmi:XMI xmlns:xmi=\"urn:xmi\"/>"
                .getBytes(StandardCharsets.UTF_8);
        when(service.export(modelId)).thenReturn(new XmiExportDocument("modelo-ventas.xmi", body));

        mvc.perform(get("/api/uml-models/{modelId}/export/xmi", modelId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/xml"))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("filename=\"modelo-ventas.xmi\"")))
                .andExpect(content().bytes(body));
    }
}
