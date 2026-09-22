package com.casegen.versioning.controller;

import com.casegen.versioning.dto.ModelVersionResponse;
import com.casegen.versioning.service.ModelVersionService;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/uml-models/{modelId}/versions")
public class ModelVersionController {
    private final ModelVersionService service;
    public ModelVersionController(ModelVersionService service) { this.service = service; }
    @PostMapping
    public ResponseEntity<ModelVersionResponse> create(@PathVariable UUID modelId) {
        return ResponseEntity.created(URI.create("/api/uml-models/" + modelId + "/versions")).body(service.create(modelId));
    }
    @GetMapping
    public List<ModelVersionResponse> list(@PathVariable UUID modelId) { return service.list(modelId); }
}
