package com.casegen.modeling.controller;

import com.casegen.modeling.command.CreateUmlAssociationCommand;
import com.casegen.modeling.command.CreateUmlGeneralizationCommand;
import com.casegen.modeling.command.CreateUmlRealizationCommand;
import com.casegen.modeling.command.UmlAssociationEndCommand;
import com.casegen.modeling.command.UpdateUmlAssociationCommand;
import com.casegen.modeling.dto.CreateUmlAssociationRequest;
import com.casegen.modeling.dto.CreateUmlGeneralizationRequest;
import com.casegen.modeling.dto.CreateUmlRealizationRequest;
import com.casegen.modeling.dto.UmlAssociationEndRequest;
import com.casegen.modeling.dto.UmlAssociationResponse;
import com.casegen.modeling.dto.UmlGeneralizationResponse;
import com.casegen.modeling.dto.UmlRealizationResponse;
import com.casegen.modeling.dto.UpdateUmlAssociationRequest;
import com.casegen.modeling.service.UmlRelationshipService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UmlRelationshipController {

    private final UmlRelationshipService service;

    public UmlRelationshipController(UmlRelationshipService service) {
        this.service = service;
    }

    @GetMapping("/api/uml-models/{modelId}/associations")
    public List<UmlAssociationResponse> listAssociations(@PathVariable UUID modelId) {
        return service.listAssociations(modelId);
    }

    @PostMapping("/api/uml-models/{modelId}/associations")
    public ResponseEntity<UmlAssociationResponse> createAssociation(
            @PathVariable UUID modelId, @Valid @RequestBody CreateUmlAssociationRequest request) {
        UmlAssociationResponse response = service.createAssociation(modelId,
                new CreateUmlAssociationCommand(request.name(), request.documentation(), request.packageId(),
                        toCommand(request.firstEnd()), toCommand(request.secondEnd())));
        return ResponseEntity.created(URI.create(
                "/api/uml-models/" + modelId + "/associations/" + response.id())).body(response);
    }

    @PutMapping("/api/uml-models/{modelId}/associations/{associationId}")
    public UmlAssociationResponse updateAssociation(
            @PathVariable UUID modelId, @PathVariable UUID associationId,
            @Valid @RequestBody UpdateUmlAssociationRequest request) {
        return service.updateAssociation(modelId, associationId,
                new UpdateUmlAssociationCommand(request.name(), request.documentation(), request.packageId(),
                        toCommand(request.firstEnd()), toCommand(request.secondEnd())));
    }

    @DeleteMapping("/api/uml-models/{modelId}/associations/{associationId}")
    public ResponseEntity<Void> deleteAssociation(
            @PathVariable UUID modelId, @PathVariable UUID associationId) {
        service.deleteAssociation(modelId, associationId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/uml-models/{modelId}/generalizations")
    public List<UmlGeneralizationResponse> listGeneralizations(@PathVariable UUID modelId) {
        return service.listGeneralizations(modelId);
    }

    @PostMapping("/api/uml-models/{modelId}/generalizations")
    public ResponseEntity<UmlGeneralizationResponse> createGeneralization(
            @PathVariable UUID modelId, @Valid @RequestBody CreateUmlGeneralizationRequest request) {
        UmlGeneralizationResponse response = service.createGeneralization(modelId,
                new CreateUmlGeneralizationCommand(
                        request.specificClassifierId(), request.generalClassifierId(),
                        !Boolean.FALSE.equals(request.isSubstitutable()), request.documentation()));
        return ResponseEntity.created(URI.create(
                "/api/uml-models/" + modelId + "/generalizations/" + response.id())).body(response);
    }

    @DeleteMapping("/api/uml-models/{modelId}/generalizations/{generalizationId}")
    public ResponseEntity<Void> deleteGeneralization(
            @PathVariable UUID modelId, @PathVariable UUID generalizationId) {
        service.deleteGeneralization(modelId, generalizationId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/uml-models/{modelId}/realizations")
    public List<UmlRealizationResponse> listRealizations(@PathVariable UUID modelId) {
        return service.listRealizations(modelId);
    }

    @PostMapping("/api/uml-models/{modelId}/realizations")
    public ResponseEntity<UmlRealizationResponse> createRealization(
            @PathVariable UUID modelId, @Valid @RequestBody CreateUmlRealizationRequest request) {
        UmlRealizationResponse response = service.createRealization(modelId,
                new CreateUmlRealizationCommand(
                        request.clientClassifierId(), request.supplierClassifierId(),
                        request.documentation()));
        return ResponseEntity.created(URI.create(
                "/api/uml-models/" + modelId + "/realizations/" + response.id())).body(response);
    }

    @DeleteMapping("/api/uml-models/{modelId}/realizations/{realizationId}")
    public ResponseEntity<Void> deleteRealization(
            @PathVariable UUID modelId, @PathVariable UUID realizationId) {
        service.deleteRealization(modelId, realizationId);
        return ResponseEntity.noContent().build();
    }

    private UmlAssociationEndCommand toCommand(UmlAssociationEndRequest request) {
        return new UmlAssociationEndCommand(
                request.roleName(), request.classifierId(), request.visibility(),
                request.lowerBound(), request.upperBound(), Boolean.TRUE.equals(request.navigable()),
                request.aggregation(), request.ownership());
    }
}
