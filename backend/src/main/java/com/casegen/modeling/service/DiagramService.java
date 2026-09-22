package com.casegen.modeling.service;

import com.casegen.collaboration.RealtimeEventPublisher;
import com.casegen.collaboration.RealtimeEventType;
import com.casegen.modeling.domain.Diagram;
import com.casegen.modeling.domain.DiagramEdge;
import com.casegen.modeling.domain.DiagramNode;
import com.casegen.modeling.domain.DiagramType;
import com.casegen.modeling.domain.DiagramSide;
import com.casegen.modeling.domain.UmlElement;
import com.casegen.modeling.domain.UmlElementType;
import com.casegen.modeling.domain.UmlModel;
import com.casegen.modeling.domain.UmlPackage;
import com.casegen.modeling.dto.AddDiagramEdgeRequest;
import com.casegen.modeling.dto.AddDiagramNodeRequest;
import com.casegen.modeling.dto.CreateDiagramRequest;
import com.casegen.modeling.dto.DiagramEdgeResponse;
import com.casegen.modeling.dto.DiagramNodeResponse;
import com.casegen.modeling.dto.DiagramResponse;
import com.casegen.modeling.dto.UpdateDiagramNodeGeometryRequest;
import com.casegen.modeling.dto.UpdateDiagramEdgeLayoutRequest;
import com.casegen.modeling.dto.DiagramWaypoint;
import com.casegen.modeling.exception.SemanticErrorCode;
import com.casegen.modeling.exception.SemanticException;
import com.casegen.modeling.repository.DiagramEdgeRepository;
import com.casegen.modeling.repository.DiagramNodeRepository;
import com.casegen.modeling.repository.DiagramRepository;
import com.casegen.modeling.repository.UmlElementRepository;
import com.casegen.modeling.repository.UmlModelRepository;
import com.casegen.modeling.repository.UmlPackageRepository;
import com.casegen.project.service.ProjectAuthorizationService;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DiagramService {

    private static final EnumSet<UmlElementType> EDGE_TYPES = EnumSet.of(
            UmlElementType.ASSOCIATION,
            UmlElementType.GENERALIZATION,
            UmlElementType.REALIZATION);

    private final DiagramRepository diagramRepository;
    private final DiagramNodeRepository nodeRepository;
    private final DiagramEdgeRepository edgeRepository;
    private final UmlModelRepository modelRepository;
    private final UmlPackageRepository packageRepository;
    private final UmlElementRepository elementRepository;
    private final ProjectAuthorizationService authorizationService;
    private final RealtimeEventPublisher realtimeEvents;

    public DiagramService(
            DiagramRepository diagramRepository,
            DiagramNodeRepository nodeRepository,
            DiagramEdgeRepository edgeRepository,
            UmlModelRepository modelRepository,
            UmlPackageRepository packageRepository,
            UmlElementRepository elementRepository,
            ProjectAuthorizationService authorizationService,
            RealtimeEventPublisher realtimeEvents) {
        this.diagramRepository = diagramRepository;
        this.nodeRepository = nodeRepository;
        this.edgeRepository = edgeRepository;
        this.modelRepository = modelRepository;
        this.packageRepository = packageRepository;
        this.elementRepository = elementRepository;
        this.authorizationService = authorizationService;
        this.realtimeEvents = realtimeEvents;
    }

    @Transactional
    public DiagramResponse createDiagram(UUID modelId, CreateDiagramRequest request) {
        UmlModel model = findModel(modelId);
        var creator = authorizationService.requireWrite(model.getProject().getId());
        if (request.type() != DiagramType.CLASS) {
            throw error(SemanticErrorCode.INVALID_REQUEST, HttpStatus.BAD_REQUEST,
                    "Only CLASS diagrams are supported");
        }
        UmlPackage ownerPackage = resolveOwnerPackage(request.ownerPackageId(), modelId);
        String name = normalizeName(request.name());
        if (diagramRepository.existsByModelIdAndName(modelId, name)) {
            throw duplicateDiagramName();
        }
        try {
            Diagram diagram = diagramRepository.saveAndFlush(
                    new Diagram(model, ownerPackage, name, request.type(), creator));
            realtimeEvents.diagram(RealtimeEventType.DIAGRAM_CREATED, model.getProject().getId(),
                    modelId, diagram.getId(), diagram.getId(), diagram.getRevision());
            return toResponse(diagram);
        } catch (DataIntegrityViolationException exception) {
            throw duplicateDiagramName();
        }
    }

    @Transactional(readOnly = true)
    public List<DiagramResponse> listDiagrams(UUID modelId) {
        UmlModel model = findModel(modelId);
        authorizationService.requireRead(model.getProject().getId());
        return diagramRepository.findByModelIdOrderByNameAsc(modelId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public DiagramResponse getDiagram(UUID diagramId) {
        Diagram diagram = findDiagram(diagramId);
        authorizationService.requireRead(diagram.getModel().getProject().getId());
        return toResponse(diagram);
    }

    @Transactional
    public DiagramNodeResponse addNode(UUID diagramId, AddDiagramNodeRequest request) {
        Diagram diagram = findDiagramForUpdate(diagramId);
        authorizationService.requireWrite(diagram.getModel().getProject().getId());
        UmlElement element = resolveElement(request.umlElementId(), diagram.getModel().getId());
        if (element.getElementType() != UmlElementType.CLASSIFIER) {
            throw error(SemanticErrorCode.INVALID_DIAGRAM_NODE, HttpStatus.CONFLICT,
                    "A Class Diagram node must reference a classifier");
        }
        if (nodeRepository.existsByDiagramIdAndUmlElementId(diagramId, element.getId())) {
            throw error(SemanticErrorCode.DIAGRAM_ELEMENT_ALREADY_PRESENT, HttpStatus.CONFLICT,
                    "The UML element is already present in this diagram");
        }
        validateGeometry(request.x(), request.y(), request.width(), request.height());
        try {
            DiagramNode node = nodeRepository.saveAndFlush(new DiagramNode(
                    diagram, element, request.x(), request.y(), request.width(), request.height(),
                    request.zIndex() == null ? 0 : request.zIndex()));
            recordMutation(diagram);
            realtimeEvents.diagram(RealtimeEventType.DIAGRAM_NODE_ADDED,
                    diagram.getModel().getProject().getId(), diagram.getModel().getId(), diagramId,
                    node.getId(), diagram.getRevision());
            return toNodeResponse(node);
        } catch (DataIntegrityViolationException exception) {
            throw error(SemanticErrorCode.DIAGRAM_ELEMENT_ALREADY_PRESENT, HttpStatus.CONFLICT,
                    "The UML element is already present in this diagram");
        }
    }

    @Transactional
    public DiagramNodeResponse updateNodeGeometry(
            UUID diagramId, UUID nodeId, UpdateDiagramNodeGeometryRequest request) {
        Diagram diagram = findDiagramForUpdate(diagramId);
        authorizationService.requireWrite(diagram.getModel().getProject().getId());
        DiagramNode node = findNode(nodeId, diagramId);
        validateGeometry(request.x(), request.y(), request.width(), request.height());
        boolean resized = Double.compare(node.getWidth(), request.width()) != 0
                || Double.compare(node.getHeight(), request.height()) != 0;
        node.updateGeometry(request.x(), request.y(), request.width(), request.height(),
                request.zIndex() == null ? node.getZIndex() : request.zIndex());
        nodeRepository.saveAndFlush(node);
        recordMutation(diagram);
        realtimeEvents.diagram(resized ? RealtimeEventType.DIAGRAM_NODE_RESIZED
                        : RealtimeEventType.DIAGRAM_NODE_MOVED,
                diagram.getModel().getProject().getId(), diagram.getModel().getId(), diagramId,
                nodeId, diagram.getRevision());
        return toNodeResponse(node);
    }

    @Transactional
    public void removeNode(UUID diagramId, UUID nodeId) {
        Diagram diagram = findDiagramForUpdate(diagramId);
        authorizationService.requireWrite(diagram.getModel().getProject().getId());
        DiagramNode node = findNode(nodeId, diagramId);
        nodeRepository.delete(node);
        nodeRepository.flush();
        recordMutation(diagram);
        realtimeEvents.diagram(RealtimeEventType.DIAGRAM_NODE_REMOVED,
                diagram.getModel().getProject().getId(), diagram.getModel().getId(), diagramId,
                nodeId, diagram.getRevision());
    }

    @Transactional
    public DiagramEdgeResponse addEdge(UUID diagramId, AddDiagramEdgeRequest request) {
        Diagram diagram = findDiagramForUpdate(diagramId);
        authorizationService.requireWrite(diagram.getModel().getProject().getId());
        UmlElement element = resolveElement(request.umlElementId(), diagram.getModel().getId());
        if (!EDGE_TYPES.contains(element.getElementType())) {
            throw error(SemanticErrorCode.INVALID_DIAGRAM_EDGE, HttpStatus.CONFLICT,
                    "A Class Diagram edge must reference a UML class relationship");
        }
        DiagramNode source = findNode(request.sourceNodeId(), diagramId);
        DiagramNode target = findNode(request.targetNodeId(), diagramId);
        DiagramSide sourceSide = request.sourceSide() == null ? DiagramSide.RIGHT : request.sourceSide();
        DiagramSide targetSide = request.targetSide() == null ? DiagramSide.LEFT : request.targetSide();
        int sourceAnchorIndex = request.sourceAnchorIndex() == null ? 0 : request.sourceAnchorIndex();
        int targetAnchorIndex = request.targetAnchorIndex() == null ? 0 : request.targetAnchorIndex();
        ensureDistinctEndpoints(source, target, sourceSide, targetSide,
                sourceAnchorIndex, targetAnchorIndex);
        ensureAnchorAvailable(diagramId, source.getId(), sourceSide, sourceAnchorIndex, null);
        ensureAnchorAvailable(diagramId, target.getId(), targetSide, targetAnchorIndex, null);
        if (edgeRepository.existsByDiagramIdAndUmlElementId(diagramId, element.getId())) {
            throw error(SemanticErrorCode.DIAGRAM_ELEMENT_ALREADY_PRESENT, HttpStatus.CONFLICT,
                    "The UML relationship is already present in this diagram");
        }
        try {
            DiagramEdge edge = edgeRepository.saveAndFlush(
                    new DiagramEdge(diagram, element, source, target,
                            sourceSide, targetSide, sourceAnchorIndex, targetAnchorIndex,
                            validatedWaypoints(request.waypoints())));
            recordMutation(diagram);
            realtimeEvents.diagram(RealtimeEventType.DIAGRAM_EDGE_ADDED,
                    diagram.getModel().getProject().getId(), diagram.getModel().getId(), diagramId,
                    edge.getId(), diagram.getRevision());
            return toEdgeResponse(edge);
        } catch (DataIntegrityViolationException exception) {
            throw error(SemanticErrorCode.DIAGRAM_ELEMENT_ALREADY_PRESENT, HttpStatus.CONFLICT,
                    "The UML relationship is already present in this diagram");
        }
    }

    @Transactional
    public void removeEdge(UUID diagramId, UUID edgeId) {
        Diagram diagram = findDiagramForUpdate(diagramId);
        authorizationService.requireWrite(diagram.getModel().getProject().getId());
        DiagramEdge edge = edgeRepository.findByIdAndDiagramId(edgeId, diagramId)
                .orElseThrow(() -> error(SemanticErrorCode.DIAGRAM_EDGE_NOT_FOUND,
                        HttpStatus.NOT_FOUND, "Diagram edge does not exist"));
        edgeRepository.delete(edge);
        edgeRepository.flush();
        recordMutation(diagram);
        realtimeEvents.diagram(RealtimeEventType.DIAGRAM_EDGE_REMOVED,
                diagram.getModel().getProject().getId(), diagram.getModel().getId(), diagramId,
                edgeId, diagram.getRevision());
    }

    @Transactional
    public DiagramEdgeResponse updateEdgeLayout(
            UUID diagramId, UUID edgeId, UpdateDiagramEdgeLayoutRequest request) {
        Diagram diagram = findDiagramForUpdate(diagramId);
        authorizationService.requireWrite(diagram.getModel().getProject().getId());
        DiagramEdge edge = edgeRepository.findByIdAndDiagramId(edgeId, diagramId)
                .orElseThrow(() -> error(SemanticErrorCode.DIAGRAM_EDGE_NOT_FOUND,
                        HttpStatus.NOT_FOUND, "Diagram edge does not exist"));
        ensureDistinctEndpoints(edge.getSourceNode(), edge.getTargetNode(),
                request.sourceSide(), request.targetSide(), request.sourceAnchorIndex(),
                request.targetAnchorIndex());
        ensureAnchorAvailable(diagramId, edge.getSourceNode().getId(), request.sourceSide(),
                request.sourceAnchorIndex(), edgeId);
        ensureAnchorAvailable(diagramId, edge.getTargetNode().getId(), request.targetSide(),
                request.targetAnchorIndex(), edgeId);
        edge.updateLayout(request.sourceSide(), request.targetSide(),
                request.sourceAnchorIndex(), request.targetAnchorIndex(),
                validatedWaypoints(request.waypoints()));
        edgeRepository.saveAndFlush(edge);
        recordMutation(diagram);
        realtimeEvents.diagram(RealtimeEventType.DIAGRAM_EDGE_UPDATED,
                diagram.getModel().getProject().getId(), diagram.getModel().getId(), diagramId,
                edgeId, diagram.getRevision());
        return toEdgeResponse(edge);
    }

    private void recordMutation(Diagram diagram) {
        diagram.recordNotationMutation();
        diagramRepository.saveAndFlush(diagram);
    }

    private Diagram findDiagram(UUID diagramId) {
        return diagramRepository.findById(diagramId)
                .orElseThrow(() -> error(SemanticErrorCode.DIAGRAM_NOT_FOUND,
                        HttpStatus.NOT_FOUND, "Diagram does not exist"));
    }

    private Diagram findDiagramForUpdate(UUID diagramId) {
        return diagramRepository.findByIdForUpdate(diagramId)
                .orElseThrow(() -> error(SemanticErrorCode.DIAGRAM_NOT_FOUND,
                        HttpStatus.NOT_FOUND, "Diagram does not exist"));
    }

    private DiagramNode findNode(UUID nodeId, UUID diagramId) {
        return nodeRepository.findByIdAndDiagramId(nodeId, diagramId)
                .orElseThrow(() -> error(SemanticErrorCode.DIAGRAM_NODE_NOT_FOUND,
                        HttpStatus.NOT_FOUND, "Diagram node does not exist"));
    }

    private UmlModel findModel(UUID modelId) {
        return modelRepository.findById(modelId)
                .orElseThrow(() -> error(SemanticErrorCode.UML_MODEL_NOT_FOUND,
                        HttpStatus.NOT_FOUND, "UML model does not exist"));
    }

    private UmlElement resolveElement(UUID elementId, UUID modelId) {
        UmlElement element = elementRepository.findById(elementId)
                .orElseThrow(() -> error(SemanticErrorCode.INVALID_REQUEST,
                        HttpStatus.NOT_FOUND, "UML element does not exist"));
        if (!element.getModel().getId().equals(modelId)) {
            throw error(SemanticErrorCode.DIAGRAM_MODEL_MISMATCH, HttpStatus.CONFLICT,
                    "UML element belongs to a different model");
        }
        return element;
    }

    private UmlPackage resolveOwnerPackage(UUID packageId, UUID modelId) {
        if (packageId == null) {
            return null;
        }
        UmlPackage ownerPackage = packageRepository.findById(packageId)
                .orElseThrow(() -> error(SemanticErrorCode.PACKAGE_NOT_FOUND,
                        HttpStatus.NOT_FOUND, "Package does not exist"));
        if (!ownerPackage.getElement().getModel().getId().equals(modelId)) {
            throw error(SemanticErrorCode.DIAGRAM_MODEL_MISMATCH, HttpStatus.CONFLICT,
                    "Owner package belongs to a different model");
        }
        return ownerPackage;
    }

    private void validateGeometry(double x, double y, double width, double height) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(width)
                || !Double.isFinite(height) || width <= 0 || height <= 0) {
            throw error(SemanticErrorCode.INVALID_DIAGRAM_NODE, HttpStatus.BAD_REQUEST,
                    "Geometry must be finite and have positive width and height");
        }
    }

    private List<DiagramWaypoint> validatedWaypoints(List<DiagramWaypoint> waypoints) {
        List<DiagramWaypoint> resolved = waypoints == null ? List.of() : List.copyOf(waypoints);
        if (resolved.stream().anyMatch(point -> point == null
                || !Double.isFinite(point.x()) || !Double.isFinite(point.y()))) {
            throw error(SemanticErrorCode.INVALID_DIAGRAM_EDGE, HttpStatus.BAD_REQUEST,
                    "Waypoints must contain finite coordinates");
        }
        return resolved;
    }

    private void ensureAnchorAvailable(
            UUID diagramId, UUID nodeId, DiagramSide side, int anchorIndex, UUID excludedEdgeId) {
        boolean occupied = edgeRepository.findByDiagramIdOrderByCreatedAtAsc(diagramId).stream()
                .filter(edge -> excludedEdgeId == null || !edge.getId().equals(excludedEdgeId))
                .anyMatch(edge -> (edge.getSourceNode().getId().equals(nodeId)
                        && edge.getSourceSide() == side
                        && edge.getSourceAnchorIndex() == anchorIndex)
                        || (edge.getTargetNode().getId().equals(nodeId)
                        && edge.getTargetSide() == side
                        && edge.getTargetAnchorIndex() == anchorIndex));
        if (occupied) {
            throw error(SemanticErrorCode.INVALID_DIAGRAM_EDGE, HttpStatus.CONFLICT,
                    "The requested diagram anchor is already occupied");
        }
    }

    private void ensureDistinctEndpoints(
            DiagramNode source, DiagramNode target, DiagramSide sourceSide,
            DiagramSide targetSide, int sourceAnchorIndex, int targetAnchorIndex) {
        if (source.getId().equals(target.getId()) && sourceSide == targetSide
                && sourceAnchorIndex == targetAnchorIndex) {
            throw error(SemanticErrorCode.INVALID_DIAGRAM_EDGE, HttpStatus.CONFLICT,
                    "A self relationship must use two distinct anchors");
        }
    }

    private String normalizeName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw error(SemanticErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST,
                    "Diagram name is required");
        }
        return name.trim();
    }

    private DiagramResponse toResponse(Diagram diagram) {
        return new DiagramResponse(
                diagram.getId(),
                diagram.getModel().getId(),
                diagram.getOwnerPackage() == null ? null : diagram.getOwnerPackage().getElementId(),
                diagram.getName(),
                diagram.getType(),
                diagram.getRevision(),
                diagram.getLockVersion(),
                diagram.getCreatedBy() == null ? null : diagram.getCreatedBy().getId(),
                diagram.getCreatedAt(),
                diagram.getUpdatedAt(),
                nodeRepository.findByDiagramIdOrderByZIndexAscCreatedAtAsc(diagram.getId()).stream()
                        .map(this::toNodeResponse)
                        .toList(),
                edgeRepository.findByDiagramIdOrderByCreatedAtAsc(diagram.getId()).stream()
                        .map(this::toEdgeResponse)
                        .toList());
    }

    private DiagramNodeResponse toNodeResponse(DiagramNode node) {
        return new DiagramNodeResponse(
                node.getId(), node.getUmlElement().getId(), node.getX(), node.getY(),
                node.getWidth(), node.getHeight(), node.getZIndex(),
                node.getCreatedAt(), node.getUpdatedAt());
    }

    private DiagramEdgeResponse toEdgeResponse(DiagramEdge edge) {
        return new DiagramEdgeResponse(
                edge.getId(), edge.getUmlElement().getId(), edge.getSourceNode().getId(),
                edge.getTargetNode().getId(), edge.getSourceSide(), edge.getTargetSide(),
                edge.getSourceAnchorIndex(), edge.getTargetAnchorIndex(), edge.getWaypoints(),
                edge.getCreatedAt(), edge.getUpdatedAt());
    }

    private SemanticException duplicateDiagramName() {
        return error(SemanticErrorCode.DUPLICATE_DIAGRAM_NAME, HttpStatus.CONFLICT,
                "A diagram with this name already exists in the UML model");
    }

    private SemanticException error(SemanticErrorCode code, HttpStatus status, String message) {
        return new SemanticException(code, status, message);
    }
}
