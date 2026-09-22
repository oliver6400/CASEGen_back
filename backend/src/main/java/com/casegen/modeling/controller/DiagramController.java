package com.casegen.modeling.controller;

import com.casegen.modeling.dto.AddDiagramEdgeRequest;
import com.casegen.modeling.dto.AddDiagramNodeRequest;
import com.casegen.modeling.dto.CreateDiagramRequest;
import com.casegen.modeling.dto.DiagramEdgeResponse;
import com.casegen.modeling.dto.DiagramNodeResponse;
import com.casegen.modeling.dto.DiagramResponse;
import com.casegen.modeling.dto.UpdateDiagramNodeGeometryRequest;
import com.casegen.modeling.dto.UpdateDiagramEdgeLayoutRequest;
import com.casegen.modeling.service.DiagramService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DiagramController {

    private final DiagramService service;

    public DiagramController(DiagramService service) {
        this.service = service;
    }

    @GetMapping("/api/uml-models/{modelId}/diagrams")
    public List<DiagramResponse> list(@PathVariable UUID modelId) {
        return service.listDiagrams(modelId);
    }

    @PostMapping("/api/uml-models/{modelId}/diagrams")
    public ResponseEntity<DiagramResponse> create(
            @PathVariable UUID modelId, @Valid @RequestBody CreateDiagramRequest request) {
        DiagramResponse response = service.createDiagram(modelId, request);
        return ResponseEntity.created(URI.create("/api/diagrams/" + response.id())).body(response);
    }

    @GetMapping("/api/diagrams/{diagramId}")
    public DiagramResponse get(@PathVariable UUID diagramId) {
        return service.getDiagram(diagramId);
    }

    @PostMapping("/api/diagrams/{diagramId}/nodes")
    public ResponseEntity<DiagramNodeResponse> addNode(
            @PathVariable UUID diagramId, @Valid @RequestBody AddDiagramNodeRequest request) {
        DiagramNodeResponse response = service.addNode(diagramId, request);
        return ResponseEntity.created(URI.create(
                "/api/diagrams/" + diagramId + "/nodes/" + response.id())).body(response);
    }

    @PatchMapping("/api/diagrams/{diagramId}/nodes/{nodeId}/geometry")
    public DiagramNodeResponse updateGeometry(
            @PathVariable UUID diagramId, @PathVariable UUID nodeId,
            @Valid @RequestBody UpdateDiagramNodeGeometryRequest request) {
        return service.updateNodeGeometry(diagramId, nodeId, request);
    }

    @DeleteMapping("/api/diagrams/{diagramId}/nodes/{nodeId}")
    public ResponseEntity<Void> removeNode(
            @PathVariable UUID diagramId, @PathVariable UUID nodeId) {
        service.removeNode(diagramId, nodeId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/diagrams/{diagramId}/edges")
    public ResponseEntity<DiagramEdgeResponse> addEdge(
            @PathVariable UUID diagramId, @Valid @RequestBody AddDiagramEdgeRequest request) {
        DiagramEdgeResponse response = service.addEdge(diagramId, request);
        return ResponseEntity.created(URI.create(
                "/api/diagrams/" + diagramId + "/edges/" + response.id())).body(response);
    }

    @DeleteMapping("/api/diagrams/{diagramId}/edges/{edgeId}")
    public ResponseEntity<Void> removeEdge(
            @PathVariable UUID diagramId, @PathVariable UUID edgeId) {
        service.removeEdge(diagramId, edgeId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/api/diagrams/{diagramId}/edges/{edgeId}/layout")
    public DiagramEdgeResponse updateEdgeLayout(
            @PathVariable UUID diagramId, @PathVariable UUID edgeId,
            @Valid @RequestBody UpdateDiagramEdgeLayoutRequest request) {
        return service.updateEdgeLayout(diagramId, edgeId, request);
    }
}
