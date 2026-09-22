package com.casegen.generation.backend;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.casegen.generation.backend.controller.GenerationController;
import com.casegen.generation.backend.dto.GenerationDtos.StartResponse;
import com.casegen.generation.backend.service.GenerationService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;

class GenerationControllerTest {
    @Test
    void postReturns202AndGenerationId() {
        GenerationService service=mock(GenerationService.class);
        UUID logicalId=UUID.randomUUID();UUID generationId=UUID.randomUUID();
        when(service.generate(logicalId)).thenReturn(new StartResponse(generationId,"QUEUED"));
        var response=new GenerationController(service).generate(logicalId);
        assertEquals(HttpStatus.ACCEPTED,response.getStatusCode());
        assertEquals(generationId,response.getBody().generationId());
        assertEquals("QUEUED",response.getBody().status());
    }

    @Test
    void downloadUsesAttachmentNameAndExistingArtifactBytes()throws Exception {
        GenerationService service=mock(GenerationService.class);UUID runId=UUID.randomUUID(),artifactId=UUID.randomUUID();byte[] bytes={1,2,3};
        when(service.download(runId,artifactId)).thenReturn(new GenerationService.ArtifactDownload("ventas-api-v3.zip",bytes.length,new ByteArrayResource(bytes)));
        var response=new GenerationController(service).download(runId,artifactId);
        assertEquals(HttpStatus.OK,response.getStatusCode());assertEquals(bytes.length,response.getHeaders().getContentLength());assertTrue(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION).contains("ventas-api-v3.zip"));assertArrayEquals(bytes,response.getBody().getInputStream().readAllBytes());
    }
}
