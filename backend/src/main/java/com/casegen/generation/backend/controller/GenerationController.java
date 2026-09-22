package com.casegen.generation.backend.controller;

import com.casegen.generation.backend.dto.GenerationDtos.Response;
import com.casegen.generation.backend.dto.GenerationDtos.StartResponse;
import com.casegen.generation.backend.service.GenerationService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.core.io.Resource;
import java.nio.charset.StandardCharsets;
import org.springframework.web.bind.annotation.*;

@RestController
public class GenerationController {
    private final GenerationService service;
    public GenerationController(GenerationService service){this.service=service;}

    @PostMapping("/api/logical-models/{logicalModelId}/generations")
    public ResponseEntity<StartResponse> generate(@PathVariable UUID logicalModelId){
        return ResponseEntity.accepted().body(service.generate(logicalModelId));
    }
    @GetMapping("/api/logical-models/{logicalModelId}/generations")
    public List<Response> history(@PathVariable UUID logicalModelId){return service.history(logicalModelId);}
    @GetMapping("/api/generations/{generationId}")
    public Response get(@PathVariable UUID generationId){return service.get(generationId);}
    @GetMapping("/api/generations/{generationId}/artifacts/{artifactId}/download")
    public ResponseEntity<Resource> download(@PathVariable UUID generationId,@PathVariable UUID artifactId) throws java.io.IOException {
        var download=service.download(generationId,artifactId);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).contentLength(download.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(download.fileName(),StandardCharsets.UTF_8).build().toString())
                .body(download.resource());
    }
}
