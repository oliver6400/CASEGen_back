package com.casegen.modeling.controller;

import com.casegen.modeling.command.CreateUmlClassifierCommand;
import com.casegen.modeling.command.CreateUmlModelCommand;
import com.casegen.modeling.command.CreateUmlOperationCommand;
import com.casegen.modeling.command.CreateUmlPackageCommand;
import com.casegen.modeling.command.CreateUmlParameterCommand;
import com.casegen.modeling.command.CreateUmlPropertyCommand;
import com.casegen.modeling.dto.CreateUmlClassifierRequest;
import com.casegen.modeling.dto.CreateUmlModelRequest;
import com.casegen.modeling.dto.CreateUmlOperationRequest;
import com.casegen.modeling.dto.CreateUmlPackageRequest;
import com.casegen.modeling.dto.CreateUmlParameterRequest;
import com.casegen.modeling.dto.CreateUmlPropertyRequest;
import com.casegen.modeling.dto.UmlClassifierResponse;
import com.casegen.modeling.dto.UmlModelResponse;
import com.casegen.modeling.dto.UmlOperationResponse;
import com.casegen.modeling.dto.UmlPackageResponse;
import com.casegen.modeling.dto.UmlParameterResponse;
import com.casegen.modeling.dto.UmlPropertyResponse;
import com.casegen.modeling.service.UmlModelService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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

    @PutMapping("/api/uml-models/{modelId}/classifiers/{classifierId}")
    public UmlClassifierResponse updateClassifier(
            @PathVariable UUID modelId, @PathVariable UUID classifierId,
            @Valid @RequestBody CreateUmlClassifierRequest request) {
        return service.updateClassifier(modelId, classifierId, new CreateUmlClassifierCommand(
                request.name(), request.documentation(), request.packageId(), request.kind(),
                request.visibility(), request.isAbstract(), request.isActive()));
    }

    @DeleteMapping("/api/uml-models/{modelId}/classifiers/{classifierId}")
    public ResponseEntity<Void> deleteClassifier(
            @PathVariable UUID modelId, @PathVariable UUID classifierId) {
        service.deleteClassifier(modelId, classifierId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/uml-models/{modelId}/classifiers/{classifierId}/properties")
    public ResponseEntity<UmlPropertyResponse> createProperty(
            @PathVariable UUID modelId,
            @PathVariable UUID classifierId,
            @Valid @RequestBody CreateUmlPropertyRequest request) {
        UmlPropertyResponse response = service.createProperty(
                modelId,
                classifierId,
                new CreateUmlPropertyCommand(
                        request.name(),
                        request.documentation(),
                        request.visibility(),
                        request.typeKind(),
                        request.primitiveType(),
                        request.typeClassifierId(),
                        request.lowerBound(),
                        request.upperBound(),
                        Boolean.TRUE.equals(request.isStatic()),
                        Boolean.TRUE.equals(request.isReadOnly()),
                        Boolean.TRUE.equals(request.isDerived())));
        return ResponseEntity.created(URI.create(
                "/api/uml-models/" + modelId + "/classifiers/" + classifierId
                        + "/properties/" + response.id())).body(response);
    }

    @GetMapping("/api/uml-models/{modelId}/classifiers/{classifierId}/properties")
    public List<UmlPropertyResponse> listProperties(
            @PathVariable UUID modelId, @PathVariable UUID classifierId) {
        return service.listProperties(modelId, classifierId);
    }

    @PutMapping("/api/uml-models/{modelId}/classifiers/{classifierId}/properties/{propertyId}")
    public UmlPropertyResponse updateProperty(
            @PathVariable UUID modelId, @PathVariable UUID classifierId, @PathVariable UUID propertyId,
            @Valid @RequestBody CreateUmlPropertyRequest request) {
        return service.updateProperty(modelId, classifierId, propertyId, new CreateUmlPropertyCommand(
                request.name(), request.documentation(), request.visibility(), request.typeKind(),
                request.primitiveType(), request.typeClassifierId(), request.lowerBound(), request.upperBound(),
                Boolean.TRUE.equals(request.isStatic()), Boolean.TRUE.equals(request.isReadOnly()),
                Boolean.TRUE.equals(request.isDerived())));
    }

    @DeleteMapping("/api/uml-models/{modelId}/classifiers/{classifierId}/properties/{propertyId}")
    public ResponseEntity<Void> deleteProperty(
            @PathVariable UUID modelId, @PathVariable UUID classifierId, @PathVariable UUID propertyId) {
        service.deleteProperty(modelId, classifierId, propertyId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/uml-models/{modelId}/classifiers/{classifierId}/operations")
    public ResponseEntity<UmlOperationResponse> createOperation(
            @PathVariable UUID modelId,
            @PathVariable UUID classifierId,
            @Valid @RequestBody CreateUmlOperationRequest request) {
        UmlOperationResponse response = service.createOperation(
                modelId,
                classifierId,
                new CreateUmlOperationCommand(
                        request.name(),
                        request.documentation(),
                        request.visibility(),
                        Boolean.TRUE.equals(request.isAbstract()),
                        Boolean.TRUE.equals(request.isStatic()),
                        Boolean.TRUE.equals(request.isQuery())));
        return ResponseEntity.created(URI.create(
                "/api/uml-models/" + modelId + "/classifiers/" + classifierId
                        + "/operations/" + response.id())).body(response);
    }

    @GetMapping("/api/uml-models/{modelId}/classifiers/{classifierId}/operations")
    public List<UmlOperationResponse> listOperations(
            @PathVariable UUID modelId, @PathVariable UUID classifierId) {
        return service.listOperations(modelId, classifierId);
    }

    @PutMapping("/api/uml-models/{modelId}/classifiers/{classifierId}/operations/{operationId}")
    public UmlOperationResponse updateOperation(
            @PathVariable UUID modelId, @PathVariable UUID classifierId, @PathVariable UUID operationId,
            @Valid @RequestBody CreateUmlOperationRequest request) {
        return service.updateOperation(modelId, classifierId, operationId, new CreateUmlOperationCommand(
                request.name(), request.documentation(), request.visibility(),
                Boolean.TRUE.equals(request.isAbstract()), Boolean.TRUE.equals(request.isStatic()),
                Boolean.TRUE.equals(request.isQuery())));
    }

    @DeleteMapping("/api/uml-models/{modelId}/classifiers/{classifierId}/operations/{operationId}")
    public ResponseEntity<Void> deleteOperation(
            @PathVariable UUID modelId, @PathVariable UUID classifierId, @PathVariable UUID operationId) {
        service.deleteOperation(modelId, classifierId, operationId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/uml-models/{modelId}/operations/{operationId}/parameters")
    public ResponseEntity<UmlParameterResponse> createParameter(
            @PathVariable UUID modelId,
            @PathVariable UUID operationId,
            @Valid @RequestBody CreateUmlParameterRequest request) {
        UmlParameterResponse response = service.createParameter(
                modelId,
                operationId,
                new CreateUmlParameterCommand(
                        request.name(),
                        request.documentation(),
                        request.direction(),
                        request.typeKind(),
                        request.primitiveType(),
                        request.typeClassifierId(),
                        request.lowerBound(),
                        request.upperBound(),
                        request.position()));
        return ResponseEntity.created(URI.create(
                "/api/uml-models/" + modelId + "/operations/" + operationId
                        + "/parameters/" + response.id())).body(response);
    }

    @GetMapping("/api/uml-models/{modelId}/operations/{operationId}/parameters")
    public List<UmlParameterResponse> listParameters(
            @PathVariable UUID modelId, @PathVariable UUID operationId) {
        return service.listParameters(modelId, operationId);
    }

    @PutMapping("/api/uml-models/{modelId}/operations/{operationId}/parameters/{parameterId}")
    public UmlParameterResponse updateParameter(
            @PathVariable UUID modelId, @PathVariable UUID operationId, @PathVariable UUID parameterId,
            @Valid @RequestBody CreateUmlParameterRequest request) {
        return service.updateParameter(modelId, operationId, parameterId, new CreateUmlParameterCommand(
                request.name(), request.documentation(), request.direction(), request.typeKind(),
                request.primitiveType(), request.typeClassifierId(), request.lowerBound(), request.upperBound(),
                request.position()));
    }

    @DeleteMapping("/api/uml-models/{modelId}/operations/{operationId}/parameters/{parameterId}")
    public ResponseEntity<Void> deleteParameter(
            @PathVariable UUID modelId, @PathVariable UUID operationId, @PathVariable UUID parameterId) {
        service.deleteParameter(modelId, operationId, parameterId);
        return ResponseEntity.noContent().build();
    }
}
