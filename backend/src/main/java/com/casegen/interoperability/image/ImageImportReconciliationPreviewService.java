package com.casegen.interoperability.image;

import static com.casegen.interoperability.image.ImageImportReconciliationPreviewResponse.Action.*;
import static com.casegen.interoperability.image.ImageImportReconciliationPreviewResponse.ElementKind.*;

import com.casegen.interoperability.image.DetectedClassDiagram.DetectedAttribute;
import com.casegen.interoperability.image.DetectedClassDiagram.DetectedClassifier;
import com.casegen.interoperability.image.DetectedClassDiagram.DetectedOperation;
import com.casegen.interoperability.image.DetectedClassDiagram.DetectedParameter;
import com.casegen.interoperability.image.DetectedClassDiagram.DetectedRelationship;
import com.casegen.interoperability.image.DetectedClassDiagram.DetectedType;
import com.casegen.interoperability.image.ImageImportReconciliationPreviewResponse.Candidate;
import com.casegen.interoperability.image.ImageImportReconciliationPreviewResponse.ExistingDiagramNode;
import com.casegen.interoperability.image.ImageImportReconciliationPreviewResponse.ReconciliationItem;
import com.casegen.modeling.domain.AggregationKind;
import com.casegen.modeling.domain.Diagram;
import com.casegen.modeling.domain.ParameterDirection;
import com.casegen.modeling.domain.UmlAssociation;
import com.casegen.modeling.domain.UmlAssociationEnd;
import com.casegen.modeling.domain.UmlClassifier;
import com.casegen.modeling.domain.UmlGeneralization;
import com.casegen.modeling.domain.UmlOperation;
import com.casegen.modeling.domain.UmlParameter;
import com.casegen.modeling.domain.UmlProperty;
import com.casegen.modeling.domain.UmlRealization;
import com.casegen.modeling.domain.UmlModel;
import com.casegen.modeling.repository.DiagramNodeRepository;
import com.casegen.modeling.repository.DiagramRepository;
import com.casegen.modeling.repository.UmlAssociationEndRepository;
import com.casegen.modeling.repository.UmlAssociationRepository;
import com.casegen.modeling.repository.UmlClassifierRepository;
import com.casegen.modeling.repository.UmlGeneralizationRepository;
import com.casegen.modeling.repository.UmlModelRepository;
import com.casegen.modeling.repository.UmlOperationRepository;
import com.casegen.modeling.repository.UmlPackageRepository;
import com.casegen.modeling.repository.UmlParameterRepository;
import com.casegen.modeling.repository.UmlPropertyRepository;
import com.casegen.modeling.repository.UmlRealizationRepository;
import com.casegen.project.service.ProjectAuthorizationService;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Builds a read-only reconciliation plan. It never applies any suggested action. */
@Service
public class ImageImportReconciliationPreviewService {
    private final ProjectAuthorizationService authorization;
    private final UmlModelRepository models;
    private final DiagramRepository diagrams;
    private final UmlClassifierRepository classifiers;
    private final UmlPackageRepository packages;
    private final UmlPropertyRepository properties;
    private final UmlOperationRepository operations;
    private final UmlParameterRepository parameters;
    private final UmlAssociationRepository associations;
    private final UmlAssociationEndRepository associationEnds;
    private final UmlGeneralizationRepository generalizations;
    private final UmlRealizationRepository realizations;
    private final DiagramNodeRepository nodes;

    public ImageImportReconciliationPreviewService(
            ProjectAuthorizationService authorization,
            UmlModelRepository models,
            DiagramRepository diagrams,
            UmlClassifierRepository classifiers,
            UmlPackageRepository packages,
            UmlPropertyRepository properties,
            UmlOperationRepository operations,
            UmlParameterRepository parameters,
            UmlAssociationRepository associations,
            UmlAssociationEndRepository associationEnds,
            UmlGeneralizationRepository generalizations,
            UmlRealizationRepository realizations,
            DiagramNodeRepository nodes) {
        this.authorization = authorization;
        this.models = models;
        this.diagrams = diagrams;
        this.classifiers = classifiers;
        this.packages = packages;
        this.properties = properties;
        this.operations = operations;
        this.parameters = parameters;
        this.associations = associations;
        this.associationEnds = associationEnds;
        this.generalizations = generalizations;
        this.realizations = realizations;
        this.nodes = nodes;
    }

    @Transactional(readOnly = true)
    public ImageImportReconciliationPreviewResponse preview(
            UUID projectId, ImageImportReconciliationPreviewRequest request) {
        authorization.requireWrite(projectId);
        UmlModel model = models.findById(request.targetModelId())
                .orElseThrow(() -> notFound("IMAGE_IMPORT_TARGET_MODEL_NOT_FOUND",
                        "The target UML model does not exist in this project"));
        if (!model.getProject().getId().equals(projectId)) {
            throw notFound("IMAGE_IMPORT_TARGET_MODEL_NOT_FOUND",
                    "The target UML model does not exist in this project");
        }
        Diagram diagram = diagrams.findById(request.targetDiagramId())
                .orElseThrow(() -> notFound("IMAGE_IMPORT_TARGET_DIAGRAM_NOT_FOUND",
                        "The target diagram does not exist in this model"));
        if (!diagram.getModel().getId().equals(model.getId())) {
            throw notFound("IMAGE_IMPORT_TARGET_DIAGRAM_NOT_FOUND",
                    "The target diagram does not exist in this model");
        }
        if (request.targetPackageId() != null) {
            var targetPackage = packages.findById(request.targetPackageId())
                    .orElseThrow(() -> notFound("IMAGE_IMPORT_TARGET_PACKAGE_NOT_FOUND",
                            "The target package does not exist in this model"));
            if (!targetPackage.getElement().getModel().getId().equals(model.getId())) {
                throw notFound("IMAGE_IMPORT_TARGET_PACKAGE_NOT_FOUND",
                        "The target package does not exist in this model");
            }
        }
        requireCurrentRevision(model.getRevision(), request.expectedModelRevision(),
                "IMAGE_IMPORT_STALE_MODEL_REVISION", "The UML model changed after the import was prepared");
        requireCurrentRevision(diagram.getRevision(), request.expectedDiagramRevision(),
                "IMAGE_IMPORT_STALE_DIAGRAM_REVISION", "The diagram changed after the import was prepared");
        validateProposal(request.proposal());

        List<UmlClassifier> existingClassifiers = classifiers
                .findByElementModelIdOrderByElementNameAsc(model.getId());
        List<ExistingDiagramNode> existingNodes = nodes
                .findByDiagramIdOrderByZIndexAscCreatedAtAsc(diagram.getId()).stream()
                .map(node -> new ExistingDiagramNode(node.getId(), node.getUmlElement().getId(),
                        node.getX(), node.getY(), node.getWidth(), node.getHeight(), node.getZIndex()))
                .toList();
        Map<UUID, UUID> nodeByElement = new HashMap<>();
        existingNodes.forEach(node -> nodeByElement.put(node.umlElementId(), node.nodeId()));

        Map<String, ClassifierResolution> resolutions = new HashMap<>();
        List<ReconciliationItem> items = new ArrayList<>();
        for (DetectedClassifier detected : request.proposal().detectedClassifiers()) {
            ClassifierResolution resolution = resolveClassifier(
                    detected, request.targetPackageId(), existingClassifiers, nodeByElement);
            resolutions.put(detected.id(), resolution);
            items.add(resolution.item());
        }
        request.proposal().detectedAttributes().forEach(value ->
                items.add(reconcileAttribute(value, resolutions)));
        request.proposal().detectedOperations().forEach(value ->
                items.add(reconcileOperation(value, resolutions)));

        ExistingRelationships existingRelationships = loadRelationships(model.getId());
        request.proposal().detectedRelationships().forEach(value ->
                items.add(reconcileRelationship(value, resolutions, existingRelationships)));

        return new ImageImportReconciliationPreviewResponse(projectId, model.getId(), diagram.getId(),
                request.targetPackageId(), model.getRevision(), diagram.getRevision(), items, existingNodes);
    }

    private ClassifierResolution resolveClassifier(DetectedClassifier detected, UUID targetPackageId,
            List<UmlClassifier> existing, Map<UUID, UUID> nodeByElement) {
        List<UmlClassifier> matches = existing.stream()
                .filter(value -> java.util.Objects.equals(
                        value.getUmlPackage() == null ? null : value.getUmlPackage().getElementId(),
                        targetPackageId))
                .filter(value -> normalize(value.getElement().getName()).equals(normalize(detected.name())))
                .toList();
        List<Candidate> candidates = matches.stream().map(value -> candidate(value, nodeByElement)).toList();
        if (matches.isEmpty()) {
            return new ClassifierResolution(null, CREATE, new ReconciliationItem(detected.id(), CLASSIFIER,
                    detected.name(), CREATE, false, "NO_NAME_MATCH",
                    "No classifier in the selected package has the same normalized name; creation is safe to propose.",
                    List.of(), List.of()));
        }
        if (matches.size() > 1) {
            return new ClassifierResolution(null, CONFLICT, new ReconciliationItem(detected.id(), CLASSIFIER,
                    detected.name(), CONFLICT, true, "AMBIGUOUS_CLASSIFIER_MATCH",
                    "More than one classifier matches the normalized name; the user must select or create one.",
                    candidates, List.of()));
        }
        UmlClassifier match = matches.get(0);
        if (!match.getKind().name().equals(upper(detected.kind()))) {
            return new ClassifierResolution(null, CONFLICT, new ReconciliationItem(detected.id(), CLASSIFIER,
                    detected.name(), CONFLICT, true, "CLASSIFIER_KIND_MISMATCH",
                    "The name matches, but CLASS and INTERFACE kinds differ; no automatic equivalence is assumed.",
                    candidates, List.of()));
        }
        return new ClassifierResolution(match.getElementId(), MAP_TO_EXISTING,
                new ReconciliationItem(detected.id(), CLASSIFIER, detected.name(), MAP_TO_EXISTING, true,
                        "NAME_PACKAGE_KIND_MATCH",
                        "Name, selected package and classifier kind match. Mapping is suggested but requires approval.",
                        candidates, List.of()));
    }

    private ReconciliationItem reconcileAttribute(DetectedAttribute detected,
            Map<String, ClassifierResolution> resolutions) {
        ClassifierResolution owner = resolutions.get(detected.ownerClassifierId());
        if (owner == null || owner.action() == CONFLICT) {
            return conflict(detected.id(), ATTRIBUTE, detected.name(), "UNRESOLVED_ATTRIBUTE_OWNER",
                    "The attribute owner must be resolved before this attribute can be reconciled.",
                    List.of(detected.ownerClassifierId()));
        }
        if (owner.existingId() == null) {
            return create(detected.id(), ATTRIBUTE, detected.name(), "NEW_CLASSIFIER_MEMBER",
                    "The owner classifier will be new, so the attribute can be created with it.",
                    List.of(detected.ownerClassifierId()));
        }
        List<UmlProperty> sameName = properties
                .findByOwnerClassifierElementIdOrderByElementNameAscElementIdAsc(owner.existingId()).stream()
                .filter(value -> normalize(value.getElement().getName()).equals(normalize(detected.name())))
                .toList();
        if (sameName.isEmpty()) {
            return create(detected.id(), ATTRIBUTE, detected.name(), "ATTRIBUTE_NAME_AVAILABLE",
                    "The mapped classifier has no attribute with this normalized name.",
                    List.of(detected.ownerClassifierId()));
        }
        List<UmlProperty> exact = sameName.stream()
                .filter(value -> sameAttributeSignature(detected, value, resolutions)).toList();
        if (exact.size() == 1) {
            return skip(detected.id(), ATTRIBUTE, detected.name(), exact.get(0).getElementId(),
                    "DUPLICATE_ATTRIBUTE_SIGNATURE",
                    "An attribute with the same owner, name, type and multiplicity already exists.",
                    List.of(detected.ownerClassifierId()));
        }
        return conflictWithIds(detected.id(), ATTRIBUTE, detected.name(), sameName.stream()
                        .map(UmlProperty::getElementId).toList(), "ATTRIBUTE_NAME_CONFLICT",
                "An attribute with this name exists but its semantic signature differs.",
                List.of(detected.ownerClassifierId()));
    }

    private ReconciliationItem reconcileOperation(DetectedOperation detected,
            Map<String, ClassifierResolution> resolutions) {
        ClassifierResolution owner = resolutions.get(detected.ownerClassifierId());
        if (owner == null || owner.action() == CONFLICT) {
            return conflict(detected.id(), OPERATION, detected.name(), "UNRESOLVED_OPERATION_OWNER",
                    "The operation owner must be resolved before its signature can be compared.",
                    List.of(detected.ownerClassifierId()));
        }
        if (owner.existingId() == null) {
            return create(detected.id(), OPERATION, detected.name(), "NEW_CLASSIFIER_MEMBER",
                    "The owner classifier will be new, so the operation can be created with it.",
                    List.of(detected.ownerClassifierId()));
        }
        List<UmlOperation> sameName = operations
                .findByOwnerClassifierElementIdOrderByElementNameAscElementIdAsc(owner.existingId()).stream()
                .filter(value -> normalize(value.getElement().getName()).equals(normalize(detected.name())))
                .toList();
        if (sameName.isEmpty()) {
            return create(detected.id(), OPERATION, detected.name(), "OPERATION_SIGNATURE_AVAILABLE",
                    "The mapped classifier has no operation with this name.",
                    List.of(detected.ownerClassifierId()));
        }
        List<UmlOperation> exact = sameName.stream()
                .filter(value -> sameOperationSignature(detected, value, resolutions)).toList();
        if (exact.size() == 1) {
            return skip(detected.id(), OPERATION, detected.name(), exact.get(0).getElementId(),
                    "DUPLICATE_OPERATION_SIGNATURE",
                    "An operation with the same ordered parameter and return signature already exists.",
                    List.of(detected.ownerClassifierId()));
        }
        return conflictWithIds(detected.id(), OPERATION, detected.name(), sameName.stream()
                        .map(UmlOperation::getElementId).toList(), "OPERATION_OVERLOAD_OR_CONFLICT",
                "Operations share the name but no existing ordered signature is identical.",
                List.of(detected.ownerClassifierId()));
    }

    private ReconciliationItem reconcileRelationship(DetectedRelationship detected,
            Map<String, ClassifierResolution> resolutions, ExistingRelationships existing) {
        ClassifierResolution source = resolutions.get(detected.source().classifierId());
        ClassifierResolution target = resolutions.get(detected.target().classifierId());
        List<String> dependencies = List.of(detected.source().classifierId(), detected.target().classifierId());
        if (source == null || target == null || source.action() == CONFLICT || target.action() == CONFLICT) {
            return conflict(detected.id(), RELATIONSHIP, detected.name(), "UNRESOLVED_RELATIONSHIP_END",
                    "Both relationship endpoints must be resolved before comparing relationships.", dependencies);
        }
        if (source.existingId() == null || target.existingId() == null) {
            return create(detected.id(), RELATIONSHIP, detected.name(), "RELATIONSHIP_USES_NEW_CLASSIFIER",
                    "At least one endpoint is new; the relationship can be created after temporary IDs are mapped.",
                    dependencies);
        }
        List<UUID> sameEndpoints = relationshipMatches(detected, source.existingId(), target.existingId(), existing);
        if (sameEndpoints.isEmpty()) {
            List<UUID> endpointConflicts = associationEndpointMatches(
                    detected, source.existingId(), target.existingId(), existing);
            if (!endpointConflicts.isEmpty()) {
                return conflictWithIds(detected.id(), RELATIONSHIP, detected.name(), endpointConflicts,
                        "RELATIONSHIP_ENDPOINT_CONFLICT",
                        "A relationship connects these classifiers, but roles, multiplicities, navigation or aggregation differ.",
                        dependencies);
            }
            return create(detected.id(), RELATIONSHIP, detected.name(), "RELATIONSHIP_NOT_FOUND",
                    "No equivalent semantic relationship exists between the resolved endpoints.", dependencies);
        }
        if (sameEndpoints.size() == 1) {
            return skip(detected.id(), RELATIONSHIP, detected.name(), sameEndpoints.get(0),
                    "DUPLICATE_RELATIONSHIP", "An equivalent semantic relationship already exists.", dependencies);
        }
        return conflictWithIds(detected.id(), RELATIONSHIP, detected.name(), sameEndpoints,
                "AMBIGUOUS_RELATIONSHIP_MATCH", "More than one existing relationship matches this detection.",
                dependencies);
    }

    private ExistingRelationships loadRelationships(UUID modelId) {
        return new ExistingRelationships(
                associations.findByElementModelIdOrderByElementCreatedAtAsc(modelId),
                generalizations.findByElementModelIdOrderByElementCreatedAtAsc(modelId),
                realizations.findByElementModelIdOrderByElementCreatedAtAsc(modelId));
    }

    private List<UUID> relationshipMatches(DetectedRelationship detected, UUID sourceId, UUID targetId,
            ExistingRelationships existing) {
        String kind = upper(detected.type());
        if ("GENERALIZATION".equals(kind)) {
            return existing.generalizations().stream()
                    .filter(value -> value.getSpecificClassifier().getElementId().equals(sourceId)
                            && value.getGeneralClassifier().getElementId().equals(targetId))
                    .map(UmlGeneralization::getElementId).toList();
        }
        if ("REALIZATION".equals(kind)) {
            return existing.realizations().stream()
                    .filter(value -> value.getClientClassifier().getElementId().equals(sourceId)
                            && value.getSupplierClassifier().getElementId().equals(targetId))
                    .map(UmlRealization::getElementId).toList();
        }
        if (!List.of("ASSOCIATION", "AGGREGATION", "COMPOSITION").contains(kind)) return List.of();
        AggregationKind aggregation = switch (kind) {
            case "AGGREGATION" -> AggregationKind.SHARED;
            case "COMPOSITION" -> AggregationKind.COMPOSITE;
            default -> AggregationKind.NONE;
        };
        List<UUID> result = new ArrayList<>();
        for (UmlAssociation association : existing.associations()) {
            List<UmlAssociationEnd> ends = associationEnds
                    .findByAssociationElementIdOrderByMemberOrderAsc(association.getElementId());
            if (ends.size() != 2) continue;
            if (sameAssociationEnd(detected.source(), sourceId, aggregation, ends.get(0))
                    && sameAssociationEnd(detected.target(), targetId, AggregationKind.NONE, ends.get(1))) {
                result.add(association.getElementId());
            } else if (aggregation == AggregationKind.NONE
                    && sameAssociationEnd(detected.source(), sourceId, AggregationKind.NONE, ends.get(1))
                    && sameAssociationEnd(detected.target(), targetId, AggregationKind.NONE, ends.get(0))) {
                result.add(association.getElementId());
            }
        }
        return result;
    }

    private List<UUID> associationEndpointMatches(DetectedRelationship detected, UUID sourceId, UUID targetId,
            ExistingRelationships existing) {
        if (!List.of("ASSOCIATION", "AGGREGATION", "COMPOSITION").contains(upper(detected.type()))) {
            return List.of();
        }
        List<UUID> result = new ArrayList<>();
        for (UmlAssociation association : existing.associations()) {
            List<UmlAssociationEnd> ends = associationEnds
                    .findByAssociationElementIdOrderByMemberOrderAsc(association.getElementId());
            if (ends.size() != 2) continue;
            UUID first = ends.get(0).getProperty().getTypeClassifier().getElementId();
            UUID second = ends.get(1).getProperty().getTypeClassifier().getElementId();
            if ((first.equals(sourceId) && second.equals(targetId))
                    || (first.equals(targetId) && second.equals(sourceId))) {
                result.add(association.getElementId());
            }
        }
        return result;
    }

    private boolean sameAssociationEnd(DetectedClassDiagram.DetectedEnd detected, UUID classifierId,
            AggregationKind aggregation, UmlAssociationEnd existing) {
        UmlProperty property = existing.getProperty();
        return detected.multiplicityKnown()
                && property.getTypeClassifier().getElementId().equals(classifierId)
                && existing.getAggregation() == aggregation
                && existing.isNavigable() == detected.navigable()
                && normalize(property.getElement().getName()).equals(normalize(detected.roleName()))
                && property.getLowerBound() == value(detected.lowerBound(), 1)
                && java.util.Objects.equals(property.getUpperBound(), detected.upperUnlimited()
                        ? null : detected.upperBound());
    }

    private boolean sameAttributeSignature(DetectedAttribute detected, UmlProperty existing,
            Map<String, ClassifierResolution> resolutions) {
        return detected.multiplicityKnown()
                && typeKey(detected.type(), resolutions).equals(typeKey(existing))
                && existing.getLowerBound() == value(detected.lowerBound(), 1)
                && java.util.Objects.equals(existing.getUpperBound(),
                        detected.upperUnlimited() ? null : detected.upperBound());
    }

    private boolean sameOperationSignature(DetectedOperation detected, UmlOperation existing,
            Map<String, ClassifierResolution> resolutions) {
        List<UmlParameter> stored = parameters.findByOperationElementIdOrderByPositionAsc(existing.getElementId());
        List<UmlParameter> storedArguments = stored.stream()
                .filter(value -> value.getDirection() != ParameterDirection.RETURN)
                .sorted(Comparator.comparingInt(UmlParameter::getPosition)).toList();
        List<DetectedParameter> detectedArguments = detected.parameters().stream()
                .filter(value -> !"RETURN".equals(upper(value.direction())))
                .sorted(Comparator.comparingInt(DetectedParameter::position)).toList();
        if (storedArguments.size() != detectedArguments.size()) return false;
        for (int index = 0; index < storedArguments.size(); index++) {
            UmlParameter storedParameter = storedArguments.get(index);
            DetectedParameter detectedParameter = detectedArguments.get(index);
            if (!storedParameter.getDirection().name().equals(upper(detectedParameter.direction()))
                    || !typeKey(storedParameter).equals(typeKey(detectedParameter.type(), resolutions))) return false;
        }
        String detectedReturn = typeKey(detected.returnType(), resolutions);
        String storedReturn = stored.stream().filter(value -> value.getDirection() == ParameterDirection.RETURN)
                .findFirst().map(this::typeKey).orElse("VOID:void");
        return storedReturn.equals(detectedReturn);
    }

    private String typeKey(DetectedType type, Map<String, ClassifierResolution> resolutions) {
        if (type == null || "VOID".equals(upper(type.kind()))) return "VOID:void";
        if ("PRIMITIVE".equals(upper(type.kind()))) return "PRIMITIVE:" + upper(type.value());
        if ("CLASSIFIER".equals(upper(type.kind()))) {
            ClassifierResolution resolution = resolutions.get(type.value());
            return resolution != null && resolution.existingId() != null
                    ? "CLASSIFIER:" + resolution.existingId() : "TEMP:" + type.value();
        }
        return "UNKNOWN:" + normalize(type.value());
    }

    private String typeKey(UmlProperty value) {
        return value.getTypeClassifier() == null
                ? "PRIMITIVE:" + value.getPrimitiveType().name()
                : "CLASSIFIER:" + value.getTypeClassifier().getElementId();
    }

    private String typeKey(UmlParameter value) {
        return value.getTypeClassifier() == null
                ? "PRIMITIVE:" + value.getPrimitiveType().name()
                : "CLASSIFIER:" + value.getTypeClassifier().getElementId();
    }

    private Candidate candidate(UmlClassifier value, Map<UUID, UUID> nodeByElement) {
        return new Candidate(value.getElementId(), value.getElement().getName(), value.getKind().name(),
                value.getUmlPackage() == null ? null : value.getUmlPackage().getElementId(),
                nodeByElement.get(value.getElementId()));
    }

    private ReconciliationItem create(String id, ImageImportReconciliationPreviewResponse.ElementKind kind,
            String name, String code, String explanation, List<String> dependencies) {
        return new ReconciliationItem(id, kind, name, CREATE, false, code, explanation, List.of(), dependencies);
    }

    private ReconciliationItem skip(String id, ImageImportReconciliationPreviewResponse.ElementKind kind,
            String name, UUID existingId, String code, String explanation, List<String> dependencies) {
        return new ReconciliationItem(id, kind, name, SKIP, true, code, explanation,
                List.of(new Candidate(existingId, name, null, null, null)), dependencies);
    }

    private ReconciliationItem conflict(String id, ImageImportReconciliationPreviewResponse.ElementKind kind,
            String name, String code, String explanation, List<String> dependencies) {
        return new ReconciliationItem(id, kind, name, CONFLICT, true, code, explanation,
                List.of(), dependencies);
    }

    private ReconciliationItem conflictWithIds(String id,
            ImageImportReconciliationPreviewResponse.ElementKind kind, String name, List<UUID> ids,
            String code, String explanation, List<String> dependencies) {
        return new ReconciliationItem(id, kind, name, CONFLICT, true, code, explanation,
                ids.stream().map(value -> new Candidate(value, name, null, null, null)).toList(), dependencies);
    }

    private void validateProposal(DetectedClassDiagram proposal) {
        try {
            DetectedClassDiagramResponseValidator.validate(proposal);
        } catch (RuntimeException exception) {
            throw new ImageImportException("INVALID_RECONCILIATION_PROPOSAL", HttpStatus.BAD_REQUEST,
                    "The detected diagram proposal is invalid", exception);
        }
    }

    private void requireCurrentRevision(long actual, long expected, String code, String message) {
        if (actual != expected) {
            throw new ImageImportException(code, HttpStatus.CONFLICT,
                    message + "; expected " + expected + " but current revision is " + actual);
        }
    }

    private ImageImportException notFound(String code, String message) {
        return new ImageImportException(code, HttpStatus.NOT_FOUND, message);
    }

    private String normalize(String value) {
        if (value == null) return "";
        String decomposed = Normalizer.normalize(value.trim(), Normalizer.Form.NFKD)
                .replaceAll("\\p{M}+", "");
        return decomposed.replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private String upper(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private int value(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private record ClassifierResolution(UUID existingId,
            ImageImportReconciliationPreviewResponse.Action action, ReconciliationItem item) { }

    private record ExistingRelationships(List<UmlAssociation> associations,
            List<UmlGeneralization> generalizations, List<UmlRealization> realizations) { }
}
