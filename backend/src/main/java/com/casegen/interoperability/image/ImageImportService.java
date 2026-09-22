package com.casegen.interoperability.image;

import static com.casegen.interoperability.image.DetectedClassDiagram.*;

import com.casegen.interoperability.xmi.ImportedClassDiagramCreator;
import com.casegen.interoperability.xmi.XmiImportData;
import com.casegen.modeling.command.*;
import com.casegen.modeling.domain.*;
import com.casegen.modeling.service.UmlModelService;
import com.casegen.modeling.service.UmlRelationshipService;
import com.casegen.project.service.ProjectAuthorizationService;
import com.casegen.validation.service.ValidationService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ImageImportService {
    private static final Set<String> MEDIA_TYPES = Set.of("image/png", "image/jpeg", "image/webp");

    private final ImageAnalysisProvider analyzer;
    private final ProjectAuthorizationService authorization;
    private final UmlModelService models;
    private final UmlRelationshipService relationships;
    private final ImportedClassDiagramCreator diagrams;
    private final ValidationService validation;
    private final long maxBytes;

    public ImageImportService(ImageAnalysisProvider analyzer,
            ProjectAuthorizationService authorization, UmlModelService models,
            UmlRelationshipService relationships, ImportedClassDiagramCreator diagrams,
            ValidationService validation,
            @Value("${casegen.image-import.max-bytes:10485760}") long maxBytes) {
        this.analyzer = analyzer;
        this.authorization = authorization;
        this.models = models;
        this.relationships = relationships;
        this.diagrams = diagrams;
        this.validation = validation;
        this.maxBytes = maxBytes;
    }

    public DetectedClassDiagram analyze(UUID projectId, byte[] content, String contentType, String fileName) {
        authorization.requireWrite(projectId);
        validateImage(content, contentType);
        DetectedClassDiagram detected = normalizeDetectedModel(
                analyzer.analyze(content, normalizeMediaType(contentType), fileName));
        try {
            DetectedClassDiagramResponseValidator.validate(detected);
        } catch (RuntimeException exception) {
            throw DetectedClassDiagramResponseValidator.invalid(analyzer.provider(), exception);
        }
        if (detected.detectedClassifiers().isEmpty()) {
            throw error("INSUFFICIENT_UML_ELEMENTS", HttpStatus.UNPROCESSABLE_ENTITY,
                    "No se pudieron identificar suficientes elementos UML.");
        }
        if (detected.confidence() < 0.65 && detected.warnings().stream()
                .noneMatch(warning -> "LOW_CONFIDENCE".equals(warning.code()))) {
            List<DetectionWarning> warnings = new ArrayList<>(detected.warnings());
            warnings.add(new DetectionWarning("LOW_CONFIDENCE",
                    "Se detectaron elementos con baja confianza. Revise la propuesta antes de crear el modelo.", null));
            return new DetectedClassDiagram(detected.suggestedModelName(), detected.detectedClassifiers(),
                    detected.detectedAttributes(), detected.detectedOperations(),
                    detected.detectedRelationships(), warnings, detected.confidence());
        }
        return detected;
    }

    public ImageImportConfigurationResponse configuration() {
        return new ImageImportConfigurationResponse(
                analyzer.configured(), analyzer.provider(), analyzer.model(), analyzer.localOcrAvailable(),
                analyzer.fallbackLocalAvailable());
    }

    @Transactional
    public ImageImportResponse confirm(UUID projectId, DetectedClassDiagram proposal) {
        UUID actorId = authorization.requireWrite(projectId).getId();
        DetectedClassDiagram normalized = normalizeDetectedModel(proposal);
        validateNormalizedModel(normalized);
        proposal = normalized;
        String modelName = required(proposal.suggestedModelName(), "model name");
        var model = models.createModel(projectId, new CreateUmlModelCommand(modelName,
                "Created from a user-confirmed class diagram image proposal"));
        UUID modelId = model.id();
        Map<String, UUID> semanticIds = new HashMap<>();

        for (DetectedClassifier detected : proposal.detectedClassifiers()) {
            var created = models.createClassifier(modelId, new CreateUmlClassifierCommand(
                    detected.name(), null, null, classifierKind(detected.kind()),
                    visibility(detected.visibility()), false, false));
            semanticIds.put(detected.id(), created.id());
            semanticIds.putIfAbsent(detected.name(), created.id());
            semanticIds.putIfAbsent(detected.name().toLowerCase(Locale.ROOT), created.id());
        }
        for (DetectedAttribute detected : proposal.detectedAttributes()) {
            ResolvedType type = resolveType(detected.type(), semanticIds);
            var created = models.createProperty(modelId, requireId(semanticIds, detected.ownerClassifierId()),
                    new CreateUmlPropertyCommand(detected.name(), null, visibility(detected.visibility()),
                            type.kind(), type.primitive(), type.classifierId(), lower(detected.lowerBound(),
                            detected.multiplicityKnown()), upper(detected.upperBound(), detected.upperUnlimited(),
                            detected.multiplicityKnown()), false, false, false));
            semanticIds.put(detected.id(), created.id());
        }
        for (DetectedOperation detected : proposal.detectedOperations()) {
            var created = models.createOperation(modelId, requireId(semanticIds, detected.ownerClassifierId()),
                    new CreateUmlOperationCommand(detected.name(), null, visibility(detected.visibility()),
                            false, false, false));
            semanticIds.put(detected.id(), created.id());
            for (DetectedParameter parameter : detected.parameters()) {
                createParameter(modelId, created.id(), parameter, semanticIds);
            }
            if (detected.returnType() != null && !"VOID".equals(upper(detected.returnType().kind()))) {
                ResolvedType returnType = resolveType(detected.returnType(), semanticIds);
                models.createParameter(modelId, created.id(), new CreateUmlParameterCommand(
                        null, null, ParameterDirection.RETURN, returnType.kind(), returnType.primitive(),
                        returnType.classifierId(), 1, 1, detected.parameters().size()));
            }
        }

        for (DetectedRelationship detected : proposal.detectedRelationships()) {
            UUID relationId = createRelationship(modelId, detected, semanticIds);
            semanticIds.put(detected.id(), relationId);
        }

        XmiImportData layoutData = layoutData(proposal);
        var diagram = diagrams.create(modelId, modelName, actorId, layoutData, semanticIds, Instant.now());
        var validationResult = validation.validate(modelId);
        long classes = proposal.detectedClassifiers().stream().filter(value -> "CLASS".equals(upper(value.kind()))).count();
        return new ImageImportResponse(modelId, modelName, (int) classes,
                proposal.detectedClassifiers().size() - (int) classes, proposal.detectedAttributes().size(),
                proposal.detectedOperations().size(), proposal.detectedRelationships().size(),
                diagram.created() ? new ImageImportResponse.DiagramResult(diagram.diagramId(), diagram.diagramName(),
                        diagram.nodeCount(), diagram.edgeCount()) : null,
                new ImageImportResponse.ValidationResult(validationResult.id(), validationResult.status(),
                        validationResult.issues().size()), proposal.warnings());
    }

    private UUID createRelationship(UUID modelId, DetectedRelationship value, Map<String, UUID> ids) {
        String kind = upper(value.type());
        UUID source = requireId(ids, value.source().classifierId());
        UUID target = requireId(ids, value.target().classifierId());
        if (Set.of("ASSOCIATION", "AGGREGATION", "COMPOSITION").contains(kind)) {
            AggregationKind aggregation = switch (kind) {
                case "AGGREGATION" -> AggregationKind.SHARED;
                case "COMPOSITION" -> AggregationKind.COMPOSITE;
                default -> AggregationKind.NONE;
            };
            var response = relationships.createAssociation(modelId, new CreateUmlAssociationCommand(
                    emptyToNull(value.name()), null, null,
                    associationEnd(value.source(), source, aggregation),
                    associationEnd(value.target(), target, AggregationKind.NONE)));
            return response.id();
        }
        if ("GENERALIZATION".equals(kind)) {
            return relationships.createGeneralization(modelId,
                    new CreateUmlGeneralizationCommand(source, target, true, null)).id();
        }
        if ("REALIZATION".equals(kind)) {
            return relationships.createRealization(modelId,
                    new CreateUmlRealizationCommand(source, target, null)).id();
        }
        throw error("UNRESOLVED_RELATIONSHIP", HttpStatus.UNPROCESSABLE_ENTITY,
                "Resolve the relationship type before creating the model: " + value.id());
    }

    private UmlAssociationEndCommand associationEnd(DetectedEnd end, UUID classifierId,
            AggregationKind aggregation) {
        return new UmlAssociationEndCommand(emptyToNull(end.roleName()), classifierId,
                visibility(end.visibility()), lower(end.lowerBound(), end.multiplicityKnown()),
                upper(end.upperBound(), end.upperUnlimited(), end.multiplicityKnown()),
                end.navigable(), aggregation, AssociationEndOwnership.ASSOCIATION);
    }

    private void createParameter(UUID modelId, UUID operationId, DetectedParameter parameter,
            Map<String, UUID> ids) {
        if ("RETURN".equals(upper(parameter.direction()))) {
            throw error("DUPLICATE_RETURN_PROPOSAL", HttpStatus.UNPROCESSABLE_ENTITY,
                    "Use the operation returnType field instead of a RETURN parameter");
        }
        ResolvedType type = resolveType(parameter.type(), ids);
        models.createParameter(modelId, operationId, new CreateUmlParameterCommand(
                parameter.name(), null, enumValue(ParameterDirection.class, parameter.direction(), "parameter direction"),
                type.kind(), type.primitive(), type.classifierId(),
                lower(parameter.lowerBound(), parameter.multiplicityKnown()),
                upper(parameter.upperBound(), parameter.upperUnlimited(), parameter.multiplicityKnown()),
                parameter.position()));
    }

    private XmiImportData layoutData(DetectedClassDiagram proposal) {
        List<XmiImportData.ClassifierData> classifiers = proposal.detectedClassifiers().stream()
                .map(value -> new XmiImportData.ClassifierData(value.id(), value.name(), null,
                        upper(value.kind()), upper(value.visibility()), false, false)).toList();
        List<XmiImportData.AssociationData> associations = new ArrayList<>();
        List<XmiImportData.GeneralizationData> generalizations = new ArrayList<>();
        List<XmiImportData.RealizationData> realizations = new ArrayList<>();
        for (DetectedRelationship value : proposal.detectedRelationships()) {
            switch (upper(value.type())) {
                case "ASSOCIATION", "AGGREGATION", "COMPOSITION" -> associations.add(
                        new XmiImportData.AssociationData(value.id(), value.name(), null,
                                layoutEnd(value.id() + ":source", value.source(), aggregation(value.type())),
                                layoutEnd(value.id() + ":target", value.target(), "NONE")));
                case "GENERALIZATION" -> generalizations.add(new XmiImportData.GeneralizationData(
                        value.id(), value.source().classifierId(), value.target().classifierId(), true));
                case "REALIZATION" -> realizations.add(new XmiImportData.RealizationData(
                        value.id(), value.source().classifierId(), value.target().classifierId()));
                default -> { }
            }
        }
        return new XmiImportData(proposal.suggestedModelName(), "image:model", List.of(), classifiers,
                List.of(), List.of(), List.of(), associations, generalizations, realizations, List.of());
    }

    private XmiImportData.AssociationEndData layoutEnd(String id, DetectedEnd end, String aggregation) {
        return new XmiImportData.AssociationEndData(id, end.roleName(), end.classifierId(),
                upper(end.visibility()), lower(end.lowerBound(), true),
                end.upperUnlimited() ? null : end.upperBound(), end.navigable(), aggregation, "ASSOCIATION");
    }

    private String aggregation(String type) {
        return switch (upper(type)) {
            case "AGGREGATION" -> "SHARED";
            case "COMPOSITION" -> "COMPOSITE";
            default -> "NONE";
        };
    }

    private DetectedClassDiagram normalizeDetectedModel(DetectedClassDiagram proposal) {
        if (proposal == null) return null;
        List<DetectionWarning> warnings = new ArrayList<>(proposal.warnings());
        List<DetectedClassifier> classifiers = proposal.detectedClassifiers().stream()
                .map(value -> normalizeClassifier(value, warnings))
                .toList();
        List<DetectedAttribute> attributes = proposal.detectedAttributes().stream()
                .map(value -> new DetectedAttribute(clean(value.id()), clean(value.ownerClassifierId()),
                        clean(value.name()), normalizeVisibility(value.visibility(), Visibility.PRIVATE),
                        normalizeType(value.type()),
                        value.lowerBound(), value.upperBound(), value.upperUnlimited(),
                        value.multiplicityKnown(), value.confidence()))
                .toList();
        List<DetectedOperation> operations = proposal.detectedOperations().stream()
                .map(value -> new DetectedOperation(clean(value.id()), clean(value.ownerClassifierId()),
                        clean(value.name()), normalizeVisibility(value.visibility(), Visibility.PUBLIC),
                        value.parameters().stream().map(this::normalizeParameter).toList(),
                        normalizeType(value.returnType()), value.confidence()))
                .toList();
        List<DetectedRelationship> detectedRelationships = proposal.detectedRelationships().stream()
                .map(this::normalizeRelationship)
                .toList();
        return new DetectedClassDiagram(clean(proposal.suggestedModelName()), classifiers, attributes,
                operations, detectedRelationships, warnings, proposal.confidence());
    }

    private DetectedClassifier normalizeClassifier(DetectedClassifier value,
            List<DetectionWarning> warnings) {
        String originalName = clean(value.name());
        boolean interfaceStereotype = originalName.matches(
                "(?is).*?(?:<<|«)\\s*interface\\s*(?:>>|»).*?");
        String name = originalName.replaceAll(
                "(?i)(?:<<|«)\\s*interface\\s*(?:>>|»)", "").trim();
        String kind = interfaceStereotype ? "INTERFACE" : upper(value.kind());
        if (name.contains(":") || name.matches(".*\\([^)]*\\).*")) {
            warnings.add(new DetectionWarning("SUSPICIOUS_CLASSIFIER_HEADER",
                    "El nombre detectado '" + name
                            + "' parece un atributo u operación. Revíselo antes de importar.",
                    value.id()));
        }
        return new DetectedClassifier(clean(value.id()), name, kind,
                normalizeVisibility(value.visibility(), Visibility.PUBLIC), value.confidence(),
                value.region());
    }

    private DetectedParameter normalizeParameter(DetectedParameter value) {
        return new DetectedParameter(clean(value.id()), clean(value.name()), upper(value.direction()),
                normalizeType(value.type()), value.position(), value.lowerBound(), value.upperBound(),
                value.upperUnlimited(), value.multiplicityKnown(), value.confidence());
    }

    private DetectedType normalizeType(DetectedType value) {
        if (value == null) return null;
        String kind = upper(value.kind());
        String typeValue = clean(value.value());
        if ("PRIMITIVE".equals(kind)) {
            String compact = typeValue.replaceAll("[ _-]", "").toUpperCase(Locale.ROOT);
            typeValue = switch (compact) {
                case "STRING" -> "STRING";
                case "INTEGER", "INT" -> "INTEGER";
                case "BOOLEAN", "BOOL" -> "BOOLEAN";
                case "REAL" -> "REAL";
                case "UNLIMITEDNATURAL" -> "UNLIMITED_NATURAL";
                default -> typeValue;
            };
        } else if ("VOID".equals(kind)) {
            typeValue = "void";
        } else if ("UNKNOWN".equals(kind) && typeValue.isBlank()) {
            typeValue = "UNKNOWN";
        }
        return new DetectedType(kind, typeValue);
    }

    private DetectedRelationship normalizeRelationship(DetectedRelationship relationship) {
        String type = upper(relationship.type());
        if (!Set.of("ASSOCIATION", "AGGREGATION", "COMPOSITION").contains(type)) {
            return new DetectedRelationship(clean(relationship.id()), type,
                    clean(relationship.name()), normalizeNonAssociationEnd(relationship.source()),
                    normalizeNonAssociationEnd(relationship.target()), relationship.confidence());
        }
        return new DetectedRelationship(clean(relationship.id()), type, clean(relationship.name()),
                normalizeAssociationEnd(relationship.source()),
                normalizeAssociationEnd(relationship.target()), relationship.confidence());
    }

    private DetectedEnd normalizeAssociationEnd(DetectedEnd end) {
        if (end == null) return null;
        return new DetectedEnd(clean(end.classifierId()), clean(end.roleName()),
                normalizeVisibility(end.visibility(), Visibility.PUBLIC), end.lowerBound(),
                end.upperBound(), end.upperUnlimited(), end.multiplicityKnown(), end.navigable());
    }

    private DetectedEnd normalizeNonAssociationEnd(DetectedEnd end) {
        if (end == null) return null;
        return new DetectedEnd(clean(end.classifierId()), clean(end.roleName()),
                clean(end.visibility()), end.lowerBound(), end.upperBound(), end.upperUnlimited(),
                end.multiplicityKnown(), end.navigable());
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeVisibility(String value, Visibility defaultValue) {
        String normalized = upper(value);
        return Set.of("", "UNKNOWN", "NOT_APPLICABLE").contains(normalized)
                ? defaultValue.name() : normalized;
    }

    private void validateNormalizedModel(DetectedClassDiagram proposal) {
        if (proposal == null || proposal.detectedClassifiers().isEmpty()) {
            throw error("INVALID_DETECTION_PROPOSAL", HttpStatus.BAD_REQUEST,
                    "At least one classifier is required");
        }
        Set<String> ids = new HashSet<>();
        Set<String> classifierIds = new HashSet<>();
        Set<String> names = new HashSet<>();
        for (DetectedClassifier classifier : proposal.detectedClassifiers()) {
            if (!ids.add(required(classifier.id(), "classifier id"))) duplicate(classifier.id());
            classifierIds.add(classifier.id());
            String key = required(classifier.name(), "classifier name").toLowerCase(Locale.ROOT);
            if (!names.add(key)) throw error("DUPLICATE_CLASSIFIER_NAME", HttpStatus.CONFLICT,
                    "Classifier names must be unique ignoring case: " + classifier.name());
            classifierKind(classifier.kind());
            validateVisibility(classifier.id(), "visibility", classifier.visibility());
        }
        proposal.detectedAttributes().forEach(value -> {
            if (!ids.add(required(value.id(), "attribute id"))) duplicate(value.id());
            required(value.name(), "attribute name"); requireClassifier(classifierIds, value.ownerClassifierId());
            validateVisibility(value.id(), "visibility", value.visibility());
            resolveTypeForValidation(value.type(), proposal.detectedClassifiers());
            lower(value.lowerBound(), value.multiplicityKnown());
        });
        proposal.detectedOperations().forEach(value -> {
            if (!ids.add(required(value.id(), "operation id"))) duplicate(value.id());
            required(value.name(), "operation name"); requireClassifier(classifierIds, value.ownerClassifierId());
            validateVisibility(value.id(), "visibility", value.visibility());
            if (value.returnType() == null || "UNKNOWN".equals(upper(value.returnType().kind())))
                throw error("UNRESOLVED_TYPE", HttpStatus.UNPROCESSABLE_ENTITY,
                        "Resolve the return type for operation " + value.name());
            if (!"VOID".equals(upper(value.returnType().kind())))
                resolveTypeForValidation(value.returnType(), proposal.detectedClassifiers());
            Set<Integer> positions = new HashSet<>();
            value.parameters().forEach(parameter -> {
                if (!ids.add(required(parameter.id(), "parameter id"))) duplicate(parameter.id());
                required(parameter.name(), "parameter name");
                ParameterDirection direction = enumValue(ParameterDirection.class, parameter.direction(), "parameter direction");
                if (direction == ParameterDirection.RETURN)
                    throw error("DUPLICATE_RETURN_PROPOSAL", HttpStatus.UNPROCESSABLE_ENTITY,
                            "Use the operation returnType field instead of a RETURN parameter");
                if (!positions.add(parameter.position()))
                    throw error("DUPLICATE_PARAMETER_POSITION", HttpStatus.CONFLICT,
                            "Parameter positions must be unique in operation " + value.name());
                resolveTypeForValidation(parameter.type(), proposal.detectedClassifiers());
                lower(parameter.lowerBound(), parameter.multiplicityKnown());
            });
        });
        proposal.detectedRelationships().forEach(value -> {
            if (!ids.add(required(value.id(), "relationship id"))) duplicate(value.id());
            requireClassifier(classifierIds, value.source().classifierId());
            requireClassifier(classifierIds, value.target().classifierId());
            String type = upper(value.type());
            if (!Set.of("ASSOCIATION", "AGGREGATION", "COMPOSITION", "GENERALIZATION", "REALIZATION").contains(type))
                throw error("UNRESOLVED_RELATIONSHIP", HttpStatus.UNPROCESSABLE_ENTITY,
                        "Resolve the relationship type: " + value.id());
            if (Set.of("ASSOCIATION", "AGGREGATION", "COMPOSITION").contains(type)) {
                validateVisibility(value.id() + ":source", "visibility", value.source().visibility());
                validateVisibility(value.id() + ":target", "visibility", value.target().visibility());
                lower(value.source().lowerBound(), value.source().multiplicityKnown());
                lower(value.target().lowerBound(), value.target().multiplicityKnown());
            }
        });
    }

    private void validateVisibility(String element, String field, String value) {
        try {
            Visibility.valueOf(upper(value));
        } catch (RuntimeException exception) {
            throw error("UNRESOLVED_DETECTED_FIELD", HttpStatus.UNPROCESSABLE_ENTITY,
                    "Cannot resolve detected field: element=" + element + ", field=" + field
                            + ", received=" + String.valueOf(value));
        }
    }

    private void resolveTypeForValidation(DetectedType type, List<DetectedClassifier> classifiers) {
        if (type == null || "UNKNOWN".equals(upper(type.kind())))
            throw error("UNRESOLVED_TYPE", HttpStatus.UNPROCESSABLE_ENTITY, "Resolve all UNKNOWN types");
        if ("PRIMITIVE".equals(upper(type.kind()))) primitive(type.value());
        else if ("CLASSIFIER".equals(upper(type.kind())) && classifiers.stream().noneMatch(value ->
                value.id().equals(type.value()) || value.name().equalsIgnoreCase(type.value())))
            throw error("UNRESOLVED_TYPE", HttpStatus.UNPROCESSABLE_ENTITY,
                    "Classifier type does not exist: " + type.value());
    }

    private ResolvedType resolveType(DetectedType type, Map<String, UUID> ids) {
        if ("PRIMITIVE".equals(upper(type.kind())))
            return new ResolvedType(TypeKind.PRIMITIVE, primitive(type.value()), null);
        if ("CLASSIFIER".equals(upper(type.kind())))
            return new ResolvedType(TypeKind.CLASSIFIER, null, requireId(ids, type.value()));
        throw error("UNRESOLVED_TYPE", HttpStatus.UNPROCESSABLE_ENTITY, "Resolve all UNKNOWN types");
    }

    private void validateImage(byte[] bytes, String contentType) {
        if (bytes == null || bytes.length == 0) throw error("EMPTY_IMAGE", HttpStatus.BAD_REQUEST, "Select an image");
        if (bytes.length > maxBytes) throw error("IMAGE_TOO_LARGE", HttpStatus.PAYLOAD_TOO_LARGE,
                "The image exceeds the maximum allowed size of " + maxBytes + " bytes");
        String media = normalizeMediaType(contentType);
        if (!MEDIA_TYPES.contains(media) || !matchesSignature(bytes, media))
            throw error("UNSUPPORTED_IMAGE", HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Only valid PNG, JPG, JPEG or WEBP images are accepted");
    }

    private boolean matchesSignature(byte[] b, String type) {
        return switch (type) {
            case "image/png" -> b.length > 7 && b[0] == (byte) 0x89 && b[1] == 0x50 && b[2] == 0x4e && b[3] == 0x47;
            case "image/jpeg" -> b.length > 2 && b[0] == (byte) 0xff && b[1] == (byte) 0xd8 && b[2] == (byte) 0xff;
            case "image/webp" -> b.length > 11 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                    && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
            default -> false;
        };
    }

    private String normalizeMediaType(String value) { return value == null ? "" : value.split(";", 2)[0].trim().toLowerCase(Locale.ROOT); }
    private ClassifierKind classifierKind(String value) { return enumValue(ClassifierKind.class, value, "classifier kind"); }
    private Visibility visibility(String value) { return enumValue(Visibility.class, value, "visibility"); }
    private PrimitiveType primitive(String value) { return enumValue(PrimitiveType.class, value, "primitive type"); }
    private <T extends Enum<T>> T enumValue(Class<T> type, String value, String field) {
        try { return Enum.valueOf(type, upper(value)); }
        catch (RuntimeException exception) { throw error("UNRESOLVED_VALUE", HttpStatus.UNPROCESSABLE_ENTITY,
                "Resolve " + field + ": " + value); }
    }
    private int lower(Integer value, boolean known) {
        if (!known || value == null) throw error("UNRESOLVED_MULTIPLICITY", HttpStatus.UNPROCESSABLE_ENTITY,
                "Resolve all unknown multiplicities before creating the model");
        return value;
    }
    private Integer upper(Integer value, boolean unlimited, boolean known) {
        if (!known) throw error("UNRESOLVED_MULTIPLICITY", HttpStatus.UNPROCESSABLE_ENTITY,
                "Resolve all unknown multiplicities before creating the model");
        return unlimited ? null : value;
    }
    private UUID requireId(Map<String, UUID> ids, String key) {
        UUID result = ids.get(key);
        if (result == null && key != null) result = ids.get(key.toLowerCase(Locale.ROOT));
        if (result == null) throw error("UNRESOLVED_REFERENCE", HttpStatus.UNPROCESSABLE_ENTITY,
                "Unknown proposal reference: " + key);
        return result;
    }
    private void requireClassifier(Set<String> ids, String id) {
        if (!ids.contains(id)) throw error("UNRESOLVED_REFERENCE", HttpStatus.UNPROCESSABLE_ENTITY,
                "Unknown classifier reference: " + id);
    }
    private String required(String value, String field) {
        if (value == null || value.isBlank()) throw error("INVALID_DETECTION_PROPOSAL", HttpStatus.BAD_REQUEST,
                field + " is required");
        return value.trim();
    }
    private void duplicate(String id) { throw error("DUPLICATE_PROPOSAL_ID", HttpStatus.CONFLICT, "Duplicate proposal id: " + id); }
    private String emptyToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String upper(String value) { return value == null ? "UNKNOWN" : value.trim().toUpperCase(Locale.ROOT); }
    private ImageImportException error(String code, HttpStatus status, String message) { return new ImageImportException(code, status, message); }
    private record ResolvedType(TypeKind kind, PrimitiveType primitive, UUID classifierId) {}
}
