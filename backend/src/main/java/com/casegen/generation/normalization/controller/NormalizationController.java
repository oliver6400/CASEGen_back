package com.casegen.generation.normalization.controller;

import com.casegen.generation.normalization.dto.NormalizationDtos.Response;
import com.casegen.generation.normalization.service.NormalizationService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class NormalizationController {
    private final NormalizationService service;
    public NormalizationController(NormalizationService service) { this.service = service; }

    @PostMapping("/api/logical-models/{logicalModelId}/normalization-runs")
    @ResponseStatus(HttpStatus.CREATED)
    public Response analyze(@PathVariable UUID logicalModelId) { return service.analyze(logicalModelId); }

    @GetMapping("/api/logical-models/{logicalModelId}/normalization-runs")
    public List<Response> list(@PathVariable UUID logicalModelId) { return service.list(logicalModelId); }

    @GetMapping("/api/normalization-runs/{runId}")
    public Response get(@PathVariable UUID runId) { return service.get(runId); }
}
