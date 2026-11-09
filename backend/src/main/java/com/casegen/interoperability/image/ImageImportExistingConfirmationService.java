package com.casegen.interoperability.image;

import static com.casegen.interoperability.image.DetectedClassDiagram.*;
import static com.casegen.interoperability.image.ImageImportReconciliationPreviewResponse.Action.*;

import com.casegen.interoperability.image.ImageImportExistingConfirmRequest.Decision;
import com.casegen.interoperability.image.ImageImportReconciliationPreviewResponse.ReconciliationItem;
import com.casegen.modeling.command.CreateUmlAssociationCommand;
import com.casegen.modeling.command.CreateUmlClassifierCommand;
import com.casegen.modeling.command.CreateUmlGeneralizationCommand;
import com.casegen.modeling.command.CreateUmlOperationCommand;
import com.casegen.modeling.command.CreateUmlParameterCommand;
import com.casegen.modeling.command.CreateUmlPropertyCommand;
import com.casegen.modeling.command.CreateUmlRealizationCommand;
import com.casegen.modeling.command.UmlAssociationEndCommand;
import com.casegen.modeling.domain.AggregationKind;
import com.casegen.modeling.domain.AssociationEndOwnership;
import com.casegen.modeling.domain.ClassifierKind;
import com.casegen.modeling.domain.Diagram;
import com.casegen.modeling.domain.ParameterDirection;
import com.casegen.modeling.domain.PrimitiveType;
import com.casegen.modeling.domain.TypeKind;
import com.casegen.modeling.domain.UmlModel;
import com.casegen.modeling.domain.UmlModelStatus;
import com.casegen.modeling.domain.Visibility;
import com.casegen.modeling.dto.AddDiagramNodeRequest;
import com.casegen.modeling.dto.DiagramNodeResponse;
import com.casegen.modeling.repository.DiagramNodeRepository;
import com.casegen.modeling.repository.DiagramRepository;
import com.casegen.modeling.repository.UmlModelRepository;
import com.casegen.modeling.repository.UmlPackageRepository;
import com.casegen.modeling.service.DiagramEdgePlacementResult;
import com.casegen.modeling.service.DiagramService;
import com.casegen.modeling.service.UmlModelService;
import com.casegen.modeling.service.UmlRelationshipService;
import com.casegen.project.service.ProjectAuthorizationService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Applies an already analyzed image proposal to one existing model and diagram atomically. */
@Service
public class ImageImportExistingConfirmationService {
    private static final double NODE_WIDTH = 310;
    private static final double NODE_HEIGHT = 180;
    private static final double NODE_GAP = 70;

    private final ProjectAuthorizationService authorization;
    private final UmlModelRepository models;
    private final DiagramRepository diagrams;
    private final UmlPackageRepository packages;
    private final DiagramNodeRepository nodes;
    private final ImageImportReconciliationPreviewService previews;
    private final UmlModelService umlModels;
    private final UmlRelationshipService relationships;
    private final DiagramService diagramService;

    public ImageImportExistingConfirmationService(
            ProjectAuthorizationService authorization,
            UmlModelRepository models,
            DiagramRepository diagrams,
            UmlPackageRepository packages,
            DiagramNodeRepository nodes,
            ImageImportReconciliationPreviewService previews,
            UmlModelService umlModels,
            UmlRelationshipService relationships,
            DiagramService diagramService) {
        this.authorization = authorization;
        this.models = models;
        this.diagrams = diagrams;
        this.packages = packages;
        this.nodes = nodes;
        this.previews = previews;
        this.umlModels = umlModels;
        this.relationships = relationships;
        this.diagramService = diagramService;
    }

    @Transactional
    public ImageImportExistingConfirmResponse confirm(
            UUID projectId, ImageImportExistingConfirmRequest request) {
        authorization.requireWrite(projectId);
        UmlModel model = models.findByIdForUpdate(request.targetModelId())
                .orElseThrow(() -> notFound("IMAGE_IMPORT_TARGET_MODEL_NOT_FOUND",
                        "The target UML model does not exist in this project"));
        if (!model.getProject().getId().equals(projectId)) {
            throw notFound("IMAGE_IMPORT_TARGET_MODEL_NOT_FOUND",
                    "The target UML model does not exist in this project");
        }
        if (model.getStatus() != UmlModelStatus.EDITABLE) {
            throw conflict("IMAGE_IMPORT_MODEL_NOT_EDITABLE", "The target UML model is not editable");
        }
        Diagram diagram = diagrams.findByIdForUpdate(request.targetDiagramId())
                .orElseThrow(() -> notFound("IMAGE_IMPORT_TARGET_DIAGRAM_NOT_FOUND",
                        "The target diagram does not exist in this model"));
        if (!diagram.getModel().getId().equals(model.getId())) {
            throw notFound("IMAGE_IMPORT_TARGET_DIAGRAM_NOT_FOUND",
                    "The target diagram does not exist in this model");
        }
        validatePackage(request.targetPackageId(), model.getId());
        requireRevision(model.getRevision(), request.expectedModelRevision(),
                "IMAGE_IMPORT_STALE_MODEL_REVISION", "The UML model changed before confirmation");
        requireRevision(diagram.getRevision(), request.expectedDiagramRevision(),
                "IMAGE_IMPORT_STALE_DIAGRAM_REVISION", "The diagram changed before confirmation");

        long previousModelRevision = model.getRevision();
        long previousDiagramRevision = diagram.getRevision();
        ImageImportReconciliationPreviewResponse current = previews.preview(projectId,
                new ImageImportReconciliationPreviewRequest(request.targetModelId(), request.targetDiagramId(),
                        request.targetPackageId(), request.expectedModelRevision(),
                        request.expectedDiagramRevision(), request.proposal()));
        Map<String, Decision> decisions = validateDecisions(request.decisions(), current);
        ApplyState state = new ApplyState();

        applyClassifiers(request, current, decisions, state);
        applyAttributes(request, current, decisions, state);
        applyOperations(request, current, decisions, state);
        applyRelationships(request, current, decisions, state);
        applyDiagram(request.targetDiagramId(), current, decisions, state);

        return new ImageImportExistingConfirmResponse(projectId, model.getId(), diagram.getId(),
                previousModelRevision, model.getRevision(), previousDiagramRevision, diagram.getRevision(),
                state.createdIds, state.reusedIds, state.createdElements, state.reusedElements,
                state.skippedElements, state.createdNodes, state.reusedNodes,
                state.createdEdges, state.reusedEdges);
    }

    private void applyClassifiers(ImageImportExistingConfirmRequest request,
            ImageImportReconciliationPreviewResponse preview, Map<String, Decision> decisions, ApplyState state) {
        for (DetectedClassifier detected : request.proposal().detectedClassifiers()) {
            Decision decision = decisions.get(detected.id());
            if (decision.action() == CREATE) {
                UUID id = umlModels.createClassifier(request.targetModelId(), new CreateUmlClassifierCommand(
                        detected.name(), null, request.targetPackageId(), enumValue(ClassifierKind.class,
                        detected.kind(), "classifier kind"), enumValue(Visibility.class,
                        detected.visibility(), "classifier visibility"), false, false)).id();
                state.created(detected.id(), id);
                state.classifierIds.put(detected.id(), id);
            } else if (decision.action() == MAP_TO_EXISTING) {
                state.reused(detected.id(), decision.candidateId());
                state.classifierIds.put(detected.id(), decision.candidateId());
            } else {
                state.skippedElements++;
            }
        }
        validateDependencies(preview, decisions, state.classifierIds);
    }

    private void applyAttributes(ImageImportExistingConfirmRequest request,
            ImageImportReconciliationPreviewResponse preview, Map<String, Decision> decisions, ApplyState state) {
        for (DetectedAttribute detected : request.proposal().detectedAttributes()) {
            Decision decision = decisions.get(detected.id());
            if (decision.action() == CREATE) {
                UUID ownerId = requireResolved(state.classifierIds, detected.ownerClassifierId(), detected.id());
                ResolvedType type = resolveType(detected.type(), state.classifierIds);
                UUID id = umlModels.createProperty(request.targetModelId(), ownerId,
                        new CreateUmlPropertyCommand(detected.name(), null,
                                enumValue(Visibility.class, detected.visibility(), "attribute visibility"),
                                type.kind(), type.primitive(), type.classifierId(),
                                lower(detected.lowerBound(), detected.multiplicityKnown()),
                                upper(detected.upperBound(), detected.upperUnlimited(),
                                        detected.multiplicityKnown()), false, false, false)).id();
                state.created(detected.id(), id);
            } else if (decision.action() == MAP_TO_EXISTING) {
                state.reused(detected.id(), decision.candidateId());
            } else state.skippedElements++;
        }
    }

    private void applyOperations(ImageImportExistingConfirmRequest request,
            ImageImportReconciliationPreviewResponse preview, Map<String, Decision> decisions, ApplyState state) {
        for (DetectedOperation detected : request.proposal().detectedOperations()) {
            Decision decision = decisions.get(detected.id());
            if (decision.action() == CREATE) {
                UUID ownerId = requireResolved(state.classifierIds, detected.ownerClassifierId(), detected.id());
                UUID operationId = umlModels.createOperation(request.targetModelId(), ownerId,
                        new CreateUmlOperationCommand(detected.name(), null,
                                enumValue(Visibility.class, detected.visibility(), "operation visibility"),
                                false, false, false)).id();
                state.created(detected.id(), operationId);
                for (DetectedParameter parameter : detected.parameters()) {
                    if ("RETURN".equals(upper(parameter.direction()))) {
                        throw invalid("DUPLICATE_RETURN_PROPOSAL",
                                "Use returnType instead of a RETURN parameter in operation " + detected.id());
                    }
                    ResolvedType type = resolveType(parameter.type(), state.classifierIds);
                    UUID id = umlModels.createParameter(request.targetModelId(), operationId,
                            new CreateUmlParameterCommand(parameter.name(), null,
                                    enumValue(ParameterDirection.class, parameter.direction(),
                                            "parameter direction"),
                                    type.kind(), type.primitive(), type.classifierId(),
                                    lower(parameter.lowerBound(), parameter.multiplicityKnown()),
                                    upper(parameter.upperBound(), parameter.upperUnlimited(),
                                            parameter.multiplicityKnown()), parameter.position())).id();
                    state.created(parameter.id(), id);
                }
                if (detected.returnType() != null
                        && !"VOID".equals(upper(detected.returnType().kind()))) {
                    ResolvedType type = resolveType(detected.returnType(), state.classifierIds);
                    UUID id = umlModels.createParameter(request.targetModelId(), operationId,
                            new CreateUmlParameterCommand(null, null, ParameterDirection.RETURN,
                                    type.kind(), type.primitive(), type.classifierId(), 1, 1,
                                    detected.parameters().size())).id();
                    state.created(detected.id() + ":return", id);
                }
            } else if (decision.action() == MAP_TO_EXISTING) {
                state.reused(detected.id(), decision.candidateId());
            } else state.skippedElements++;
        }
    }

    private void applyRelationships(ImageImportExistingConfirmRequest request,
            ImageImportReconciliationPreviewResponse preview, Map<String, Decision> decisions, ApplyState state) {
        for (DetectedRelationship detected : request.proposal().detectedRelationships()) {
            Decision decision = decisions.get(detected.id());
            if (decision.action() == CREATE) {
                UUID id = createRelationship(request.targetModelId(), request.targetPackageId(), detected,
                        state.classifierIds);
                state.created(detected.id(), id);
                state.relationshipIds.add(id);
            } else if (decision.action() == MAP_TO_EXISTING) {
                state.reused(detected.id(), decision.candidateId());
                state.relationshipIds.add(decision.candidateId());
            } else {
                state.skippedElements++;
                ReconciliationItem item = item(preview, detected.id());
                if (item.candidates().size() == 1) {
                    state.relationshipIds.add(item.candidates().getFirst().elementId());
                }
            }
        }
    }

    private UUID createRelationship(UUID modelId, UUID packageId, DetectedRelationship detected,
            Map<String, UUID> classifierIds) {
        UUID source = requireResolved(classifierIds, detected.source().classifierId(), detected.id());
        UUID target = requireResolved(classifierIds, detected.target().classifierId(), detected.id());
        return switch (upper(detected.type())) {
            case "ASSOCIATION", "AGGREGATION", "COMPOSITION" -> relationships.createAssociation(modelId,
                    new CreateUmlAssociationCommand(emptyToNull(detected.name()), null, packageId,
                            associationEnd(detected.source(), source, aggregation(detected.type())),
                            associationEnd(detected.target(), target, AggregationKind.NONE))).id();
            case "GENERALIZATION" -> relationships.createGeneralization(modelId,
                    new CreateUmlGeneralizationCommand(source, target, true, null)).id();
            case "REALIZATION" -> relationships.createRealization(modelId,
                    new CreateUmlRealizationCommand(source, target, null)).id();
            default -> throw invalid("UNRESOLVED_RELATIONSHIP",
                    "Resolve relationship type before confirmation: " + detected.id());
        };
    }

    private UmlAssociationEndCommand associationEnd(DetectedEnd end, UUID classifierId,
            AggregationKind aggregation) {
        return new UmlAssociationEndCommand(emptyToNull(end.roleName()), classifierId,
                enumValue(Visibility.class, end.visibility(), "relationship visibility"),
                lower(end.lowerBound(), end.multiplicityKnown()),
                upper(end.upperBound(), end.upperUnlimited(), end.multiplicityKnown()),
                end.navigable(), aggregation, AssociationEndOwnership.ASSOCIATION);
    }

    private void applyDiagram(UUID diagramId, ImageImportReconciliationPreviewResponse preview,
            Map<String, Decision> decisions, ApplyState state) {
        List<Rect> occupied = nodes.findByDiagramIdOrderByZIndexAscCreatedAtAsc(diagramId).stream()
                .map(node -> new Rect(node.getX(), node.getY(), node.getWidth(), node.getHeight()))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        int zIndex = occupied.size();
        Set<UUID> classifierIds = new java.util.LinkedHashSet<>(state.classifierIds.values());
        for (UUID classifierId : classifierIds) {
            var represented = nodes.findByDiagramIdAndUmlElementId(diagramId, classifierId);
            if (represented.isPresent()) {
                state.reusedNodes++;
                continue;
            }
            Rect position = nextPosition(occupied);
            DiagramNodeResponse node = diagramService.addNode(diagramId,
                    new AddDiagramNodeRequest(classifierId, position.x(), position.y(),
                            NODE_WIDTH, NODE_HEIGHT, zIndex++));
            occupied.add(new Rect(node.x(), node.y(), node.width(), node.height()));
            state.createdNodes++;
        }
        for (UUID relationshipId : state.relationshipIds) {
            DiagramEdgePlacementResult result = diagramService
                    .addRelationshipEdgeIfPossible(diagramId, relationshipId);
            if (result.status() == DiagramEdgePlacementResult.Status.CREATED) state.createdEdges++;
            else if (result.status() == DiagramEdgePlacementResult.Status.ALREADY_EXISTS) state.reusedEdges++;
            else throw conflict("IMAGE_IMPORT_RELATIONSHIP_ENDPOINT_NOT_VISIBLE",
                    "A relationship endpoint could not be represented in the target diagram");
        }
    }

    private Rect nextPosition(List<Rect> occupied) {
        if (occupied.isEmpty()) return new Rect(50, 50, NODE_WIDTH, NODE_HEIGHT);
        double startX = occupied.stream().mapToDouble(value -> value.x() + value.width()).max().orElse(50)
                + NODE_GAP;
        double startY = occupied.stream().mapToDouble(Rect::y).min().orElse(50);
        for (int row = 0; row < 1000; row++) {
            Rect candidate = new Rect(startX, startY + row * (NODE_HEIGHT + NODE_GAP),
                    NODE_WIDTH, NODE_HEIGHT);
            if (occupied.stream().noneMatch(value -> value.overlaps(candidate, NODE_GAP / 2))) {
                return candidate;
            }
        }
        throw conflict("IMAGE_IMPORT_LAYOUT_UNAVAILABLE", "No free position was found for a new node");
    }

    private Map<String, Decision> validateDecisions(List<Decision> supplied,
            ImageImportReconciliationPreviewResponse preview) {
        Map<String, Decision> decisions = new LinkedHashMap<>();
        for (Decision decision : supplied) {
            if (decision.temporaryId() == null || decision.temporaryId().isBlank()
                    || decisions.putIfAbsent(decision.temporaryId(), decision) != null) {
                throw invalid("IMAGE_IMPORT_DUPLICATE_DECISION_ID",
                        "Decision temporary IDs must be present and unique");
            }
        }
        Set<String> expected = new HashSet<>();
        for (ReconciliationItem item : preview.items()) {
            expected.add(item.temporaryId());
            Decision decision = decisions.get(item.temporaryId());
            if (decision == null) {
                throw invalid("IMAGE_IMPORT_MISSING_DECISION",
                        "A decision is required for " + item.temporaryId());
            }
            validateDecision(item, decision);
        }
        if (!expected.equals(decisions.keySet())) {
            throw invalid("IMAGE_IMPORT_UNKNOWN_DECISION_ID",
                    "Decisions contain temporary IDs that are not part of the proposal");
        }
        return decisions;
    }

    private void validateDecision(ReconciliationItem item, Decision decision) {
        if (decision.action() == CONFLICT) {
            throw conflict("IMAGE_IMPORT_UNRESOLVED_CONFLICT",
                    "Resolve all conflicts before confirmation: " + item.temporaryId());
        }
        if (decision.action() == MAP_TO_EXISTING) {
            if (decision.candidateId() == null || item.candidates().stream()
                    .noneMatch(value -> value.elementId().equals(decision.candidateId()))) {
                throw invalid("IMAGE_IMPORT_INVALID_CANDIDATE",
                        "The selected candidate is not valid for " + item.temporaryId());
            }
            return;
        }
        if (decision.candidateId() != null) {
            throw invalid("IMAGE_IMPORT_UNEXPECTED_CANDIDATE",
                    "candidateId is only valid for MAP_TO_EXISTING");
        }
        if (decision.action() == CREATE && item.suggestedAction() != CREATE) {
            throw conflict("IMAGE_IMPORT_CREATE_CONFLICT",
                    "The current model no longer allows creating " + item.temporaryId());
        }
    }

    private void validateDependencies(ImageImportReconciliationPreviewResponse preview,
            Map<String, Decision> decisions, Map<String, UUID> classifierIds) {
        for (ReconciliationItem item : preview.items()) {
            if (decisions.get(item.temporaryId()).action() == SKIP) continue;
            for (String dependency : item.dependsOnTemporaryIds()) {
                ReconciliationItem dependencyItem = preview.items().stream()
                        .filter(value -> value.temporaryId().equals(dependency)).findFirst().orElse(null);
                if (dependencyItem != null
                        && dependencyItem.elementKind()
                                == ImageImportReconciliationPreviewResponse.ElementKind.CLASSIFIER
                        && !classifierIds.containsKey(dependency)) {
                    throw invalid("IMAGE_IMPORT_INCONSISTENT_REFERENCE",
                            item.temporaryId() + " depends on unresolved classifier " + dependency);
                }
            }
        }
    }

    private ReconciliationItem item(ImageImportReconciliationPreviewResponse preview, String id) {
        return preview.items().stream().filter(value -> value.temporaryId().equals(id)).findFirst()
                .orElseThrow(() -> invalid("IMAGE_IMPORT_UNKNOWN_TEMPORARY_ID", id));
    }

    private ResolvedType resolveType(DetectedType value, Map<String, UUID> classifierIds) {
        if (value == null) throw invalid("IMAGE_IMPORT_INVALID_TYPE", "A UML type is required");
        if ("PRIMITIVE".equals(upper(value.kind()))) {
            return new ResolvedType(TypeKind.PRIMITIVE,
                    enumValue(PrimitiveType.class, value.value(), "primitive type"), null);
        }
        if ("CLASSIFIER".equals(upper(value.kind()))) {
            return new ResolvedType(TypeKind.CLASSIFIER, null,
                    requireResolved(classifierIds, value.value(), "type"));
        }
        throw invalid("IMAGE_IMPORT_INVALID_TYPE", "Resolve UML type before confirmation: " + value.value());
    }

    private UUID requireResolved(Map<String, UUID> ids, String temporaryId, String owner) {
        UUID id = ids.get(temporaryId);
        if (id == null) throw invalid("IMAGE_IMPORT_INCONSISTENT_REFERENCE",
                owner + " references unresolved classifier " + temporaryId);
        return id;
    }

    private void validatePackage(UUID packageId, UUID modelId) {
        if (packageId == null) return;
        var target = packages.findById(packageId)
                .orElseThrow(() -> notFound("IMAGE_IMPORT_TARGET_PACKAGE_NOT_FOUND",
                        "The target package does not exist in this model"));
        if (!target.getElement().getModel().getId().equals(modelId)) {
            throw notFound("IMAGE_IMPORT_TARGET_PACKAGE_NOT_FOUND",
                    "The target package does not exist in this model");
        }
    }

    private void requireRevision(long current, long expected, String code, String message) {
        if (current != expected) throw conflict(code,
                message + "; expected " + expected + " but current revision is " + current);
    }

    private int lower(Integer value, boolean known) {
        return known && value != null ? value : 1;
    }

    private Integer upper(Integer value, boolean unlimited, boolean known) {
        if (!known) return 1;
        return unlimited ? null : value;
    }

    private AggregationKind aggregation(String type) {
        return switch (upper(type)) {
            case "AGGREGATION" -> AggregationKind.SHARED;
            case "COMPOSITION" -> AggregationKind.COMPOSITE;
            default -> AggregationKind.NONE;
        };
    }

    private <E extends Enum<E>> E enumValue(Class<E> type, String value, String label) {
        try {
            return Enum.valueOf(type, upper(value));
        } catch (RuntimeException exception) {
            throw invalid("IMAGE_IMPORT_INVALID_ENUM", "Invalid " + label + ": " + value);
        }
    }

    private String upper(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private ImageImportException invalid(String code, String message) {
        return new ImageImportException(code, HttpStatus.BAD_REQUEST, message);
    }

    private ImageImportException conflict(String code, String message) {
        return new ImageImportException(code, HttpStatus.CONFLICT, message);
    }

    private ImageImportException notFound(String code, String message) {
        return new ImageImportException(code, HttpStatus.NOT_FOUND, message);
    }

    private record ResolvedType(TypeKind kind, PrimitiveType primitive, UUID classifierId) { }

    private record Rect(double x, double y, double width, double height) {
        boolean overlaps(Rect other, double gap) {
            return x < other.x + other.width + gap && x + width + gap > other.x
                    && y < other.y + other.height + gap && y + height + gap > other.y;
        }
    }

    private static final class ApplyState {
        private final Map<String, UUID> createdIds = new LinkedHashMap<>();
        private final Map<String, UUID> reusedIds = new LinkedHashMap<>();
        private final Map<String, UUID> classifierIds = new LinkedHashMap<>();
        private final Set<UUID> relationshipIds = new java.util.LinkedHashSet<>();
        private int createdElements;
        private int reusedElements;
        private int skippedElements;
        private int createdNodes;
        private int reusedNodes;
        private int createdEdges;
        private int reusedEdges;

        void created(String temporaryId, UUID id) {
            createdIds.put(temporaryId, id);
            createdElements++;
        }

        void reused(String temporaryId, UUID id) {
            reusedIds.put(temporaryId, id);
            reusedElements++;
        }
    }
}
