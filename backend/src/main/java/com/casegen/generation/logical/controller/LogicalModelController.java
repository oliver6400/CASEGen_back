package com.casegen.generation.logical.controller;

import com.casegen.generation.logical.dto.LogicalModelDtos.*;
import com.casegen.generation.logical.service.LogicalModelService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
public class LogicalModelController {
    private final LogicalModelService service;
    public LogicalModelController(LogicalModelService service) { this.service=service; }
    @PostMapping("/api/model-versions/{versionId}/logical-models")
    @ResponseStatus(HttpStatus.CREATED)
    public Response build(@PathVariable UUID versionId,@Valid @RequestBody BuildRequest request) { return service.build(versionId,request.generationProfileId()); }
    @PostMapping("/api/model-versions/{versionId}/logical-models/preview")
    public Plan preview(@PathVariable UUID versionId,@Valid @RequestBody BuildRequest request) { return service.preview(versionId,request.generationProfileId()); }
    @GetMapping("/api/uml-models/{modelId}/logical-models")
    public List<Summary> list(@PathVariable UUID modelId) { return service.list(modelId); }
    @GetMapping("/api/logical-models/{logicalModelId}")
    public Response get(@PathVariable UUID logicalModelId) { return service.get(logicalModelId); }
    @GetMapping("/api/logical-models/{logicalModelId}/tables")
    public List<Table> tables(@PathVariable UUID logicalModelId) { return service.tables(logicalModelId); }
}
