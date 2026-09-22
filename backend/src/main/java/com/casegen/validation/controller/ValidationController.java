package com.casegen.validation.controller;

import com.casegen.validation.dto.ValidationResponse;
import com.casegen.validation.service.ValidationService;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class ValidationController {
    private final ValidationService service;
    public ValidationController(ValidationService service) { this.service = service; }
    @PostMapping("/api/uml-models/{modelId}/validations")
    public ValidationResponse validate(@PathVariable UUID modelId) { return service.validate(modelId); }
    @GetMapping("/api/validations/{validationId}")
    public ValidationResponse get(@PathVariable UUID validationId) { return service.get(validationId); }
    @GetMapping("/api/uml-models/{modelId}/validations/latest")
    public ResponseEntity<ValidationResponse> latest(@PathVariable UUID modelId) { return ResponseEntity.of(service.latest(modelId)); }
}
