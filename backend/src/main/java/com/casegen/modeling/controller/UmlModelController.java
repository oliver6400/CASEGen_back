package com.casegen.modeling.controller;

import com.casegen.modeling.command.CreateUmlClassifierCommand;
import com.casegen.modeling.command.CreateUmlModelCommand;
import com.casegen.modeling.command.CreateUmlPackageCommand;
import com.casegen.modeling.dto.CreateUmlClassifierRequest;
import com.casegen.modeling.dto.CreateUmlModelRequest;
import com.casegen.modeling.dto.CreateUmlPackageRequest;
import com.casegen.modeling.dto.UmlClassifierResponse;
import com.casegen.modeling.dto.UmlModelResponse;
import com.casegen.modeling.dto.UmlPackageResponse;
import com.casegen.modeling.service.UmlModelService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UmlModelController {

    private final UmlModelService service;

    public UmlModelController(UmlModelService service) {
        this.service = service;
    }

    @PostMapping("/api/projects/{projectId}/uml-models")
    public ResponseEntity<UmlModelResponse> createModel(
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateUmlModelRequest request) {
        UmlModelResponse response = service.createModel(
                projectId, new CreateUmlModelCommand(request.name(), request.documentation()));
        return ResponseEntity.created(URI.create("/api/uml-models/" + response.id())).body(response);
    }

    @GetMapping("/api/projects/{projectId}/uml-models")
    public List<UmlModelResponse> listModels(@PathVariable UUID projectId) {
        return service.listModels(projectId);
    }

    @GetMapping("/api/uml-models/{modelId}")
    public UmlModelResponse getModel(@PathVariable UUID modelId) {
        return service.getModel(modelId);
    }

    @PostMapping("/api/uml-models/{modelId}/packages")
    public ResponseEntity<UmlPackageResponse> createPackage(
            @PathVariable UUID modelId,
            @Valid @RequestBody CreateUmlPackageRequest request) {
        UmlPackageResponse response = service.createPackage(modelId, new CreateUmlPackageCommand(
                request.name(), request.documentation(), request.parentPackageId()));
        return ResponseEntity.created(URI.create(
                "/api/uml-models/" + modelId + "/packages/" + response.id())).body(response);
    }

    @GetMapping("/api/uml-models/{modelId}/packages")
    public List<UmlPackageResponse> listPackages(@PathVariable UUID modelId) {
        return service.listPackages(modelId);
    }

    @PostMapping("/api/uml-models/{modelId}/classifiers")
    public ResponseEntity<UmlClassifierResponse> createClassifier(
            @PathVariable UUID modelId,
            @Valid @RequestBody CreateUmlClassifierRequest request) {
        UmlClassifierResponse response = service.createClassifier(modelId, new CreateUmlClassifierCommand(
                request.name(),
                request.documentation(),
                request.packageId(),
                request.kind(),
                request.visibility(),
                request.isAbstract(),
                request.isActive()));
        return ResponseEntity.created(URI.create(
                "/api/uml-models/" + modelId + "/classifiers/" + response.id())).body(response);
    }

    @GetMapping("/api/uml-models/{modelId}/classifiers")
    public List<UmlClassifierResponse> listClassifiers(@PathVariable UUID modelId) {
        return service.listClassifiers(modelId);
    }
}
