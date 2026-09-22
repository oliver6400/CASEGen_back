package com.casegen.interoperability.xmi;

import static com.casegen.interoperability.xmi.XmiImportData.*;

import com.casegen.project.repository.ProjectRepository;
import com.casegen.project.service.ProjectAuthorizationService;
import com.casegen.validation.service.ValidationService;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.w3c.dom.Document;

@Service
public class XmiImportService {
    private final SecureXmiParser parser;
    private final XmiDialectDetector detector;
    private final CasegenXmiReader casegenReader;
    private final EnterpriseArchitectXmiReader enterpriseArchitectReader;
    private final ProjectAuthorizationService authorization;
    private final ProjectRepository projects;
    private final ValidationService validation;
    private final ImportedClassDiagramCreator diagramCreator;
    private final JdbcTemplate jdbc;

    public XmiImportService(SecureXmiParser parser, XmiDialectDetector detector,
            CasegenXmiReader casegenReader, EnterpriseArchitectXmiReader enterpriseArchitectReader,
            ProjectAuthorizationService authorization, ProjectRepository projects,
            ValidationService validation, ImportedClassDiagramCreator diagramCreator, JdbcTemplate jdbc) {
        this.parser = parser;
        this.detector = detector;
        this.casegenReader = casegenReader;
        this.enterpriseArchitectReader = enterpriseArchitectReader;
        this.authorization = authorization;
        this.projects = projects;
        this.validation = validation;
        this.diagramCreator = diagramCreator;
        this.jdbc = jdbc;
    }

    @Transactional
    public XmiImportResponse importModel(UUID projectId, byte[] content) {
        var actor = authorization.requireWrite(projectId);
        if (!projects.existsById(projectId)) throw error("PROJECT_NOT_FOUND", HttpStatus.NOT_FOUND,
                "Project does not exist");
        Document document = parser.parse(content);
        XmiDialect dialect = detector.detect(document);
        XmiImportData data = switch (dialect) {
            case CASEGEN_UML_2_5_1 -> casegenReader.read(document);
            case EA_XMI_1_1_UML_1_3 -> enterpriseArchitectReader.read(document);
        };
        validateIntermediate(data);
        String name = normalizedModelName(data.modelName());
        if (Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM uml_models WHERE project_id=? AND name=?)",
                Boolean.class, projectId, name))) {
            throw error("DUPLICATE_MODEL_NAME", HttpStatus.CONFLICT,
                    "A UML model with this name already exists in the project");
        }

        UUID modelId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("""
                INSERT INTO uml_models(id,project_id,name,documentation,uml_version,revision,lock_version,status,created_by,created_at,updated_at)
                VALUES(?,?,?,NULL,'2.5.1',1,0,'EDITABLE',?,?,?)
                """, modelId, projectId, name, actor.getId(), Timestamp.from(now), Timestamp.from(now));
        Map<String, UUID> ids = allocateIds(data);
        persistPackages(modelId, data, ids, now);
        persistClassifiers(modelId, data, ids, now);
        persistFeatures(modelId, data, ids, now);
        persistRelationships(modelId, data, ids, now);
        var diagram = diagramCreator.create(modelId, name, actor.getId(), data, ids, now);

        var validationResult = validation.validate(modelId);
        long classes = data.classifiers().stream().filter(item -> "CLASS".equals(item.kind())).count();
        long interfaces = data.classifiers().stream().filter(item -> "INTERFACE".equals(item.kind())).count();
        return new XmiImportResponse(modelId, name, dialect, data.packages().size(), (int) classes,
                (int) interfaces, data.properties().size(), data.operations().size(), data.parameters().size(),
                data.associations().size(), data.generalizations().size(), data.realizations().size(),
                diagram.created() ? new XmiImportResponse.DiagramSummary(
                        diagram.diagramId(), diagram.diagramName(), diagram.nodeCount(), diagram.edgeCount()) : null,
                data.warnings(), new XmiImportResponse.ValidationSummary(validationResult.id(),
                        validationResult.status(), validationResult.issues().size(), validationResult.issues()));
    }

    private void persistPackages(UUID modelId, XmiImportData data, Map<String, UUID> ids, Instant now) {
        List<PackageData> remaining = new ArrayList<>(data.packages());
        Set<String> persisted = new HashSet<>();
        while (!remaining.isEmpty()) {
            int before = remaining.size();
            remaining.removeIf(item -> {
                if (item.parentExternalId() != null && !persisted.contains(item.parentExternalId())) return false;
                insertElement(ids.get(item.externalId()), modelId, "PACKAGE", item.name(), item.externalId(), now);
                jdbc.update("INSERT INTO uml_packages(element_id,parent_package_id) VALUES(?,?)",
                        ids.get(item.externalId()), resolveNullable(ids, item.parentExternalId()));
                persisted.add(item.externalId());
                return true;
            });
            if (remaining.size() == before) throw error("XMI_PACKAGE_CYCLE", HttpStatus.BAD_REQUEST,
                    "Package hierarchy contains a cycle or unresolved parent");
        }
    }

    private void persistClassifiers(UUID modelId, XmiImportData data, Map<String, UUID> ids, Instant now) {
        for (ClassifierData item : data.classifiers()) {
            UUID id = ids.get(item.externalId());
            insertElement(id, modelId, "CLASSIFIER", item.name(), item.externalId(), now);
            jdbc.update("""
                    INSERT INTO uml_classifiers(element_id,package_id,kind,visibility,is_abstract,is_active)
                    VALUES(?,?,?,?,?,?)
                    """, id, resolveNullable(ids, item.packageExternalId()), item.kind(), item.visibility(),
                    item.abstractClassifier(), item.active());
        }
    }

    private void persistFeatures(UUID modelId, XmiImportData data, Map<String, UUID> ids, Instant now) {
        for (PropertyData item : data.properties()) {
            UUID id = ids.get(item.externalId());
            insertElement(id, modelId, "PROPERTY", item.name(), item.externalId(), now);
            insertProperty(id, ids.get(item.ownerClassifierExternalId()), item.visibility(), item.type(),
                    item.lower(), item.upper(), item.staticProperty(), item.readOnly(), item.derived(), ids);
        }
        for (OperationData item : data.operations()) {
            UUID id = ids.get(item.externalId());
            insertElement(id, modelId, "OPERATION", item.name(), item.externalId(), now);
            jdbc.update("""
                    INSERT INTO uml_operations(element_id,owner_classifier_id,visibility,is_abstract,is_static,is_query)
                    VALUES(?,?,?,?,?,?)
                    """, id, ids.get(item.ownerClassifierExternalId()), item.visibility(), item.abstractOperation(),
                    item.staticOperation(), item.query());
        }
        for (ParameterData item : data.parameters()) {
            UUID id = ids.get(item.externalId());
            insertElement(id, modelId, "PARAMETER", item.name(), item.externalId(), now);
            jdbc.update("""
                    INSERT INTO uml_parameters(element_id,operation_id,direction,type_kind,primitive_type,type_classifier_id,lower_bound,upper_bound,position)
                    VALUES(?,?,?,?,?,?,?,?,?)
                    """, id, ids.get(item.operationExternalId()), item.direction(), item.type().typeKind(),
                    item.type().primitiveType(), resolveNullable(ids, item.type().classifierExternalId()),
                    item.lower(), item.upper(), item.position());
        }
    }

    private void persistRelationships(UUID modelId, XmiImportData data, Map<String, UUID> ids, Instant now) {
        for (AssociationData item : data.associations()) {
            UUID associationId = ids.get(item.externalId());
            insertElement(associationId, modelId, "ASSOCIATION", item.name(), item.externalId(), now);
            jdbc.update("INSERT INTO uml_associations(element_id,package_id) VALUES(?,?)", associationId,
                    resolveNullable(ids, item.packageExternalId()));
            persistEnd(modelId, associationId, item.first(), item.second(), (short) 1, ids, now);
            persistEnd(modelId, associationId, item.second(), item.first(), (short) 2, ids, now);
        }
        for (GeneralizationData item : data.generalizations()) {
            UUID id = ids.get(item.externalId());
            insertElement(id, modelId, "GENERALIZATION", null, item.externalId(), now);
            jdbc.update("""
                    INSERT INTO uml_generalizations(element_id,specific_classifier_id,general_classifier_id,is_substitutable)
                    VALUES(?,?,?,?)
                    """, id, ids.get(item.specificExternalId()), ids.get(item.generalExternalId()), item.substitutable());
        }
        for (RealizationData item : data.realizations()) {
            UUID id = ids.get(item.externalId());
            insertElement(id, modelId, "REALIZATION", null, item.externalId(), now);
            jdbc.update("INSERT INTO uml_realizations(element_id,client_classifier_id,supplier_classifier_id) VALUES(?,?,?)",
                    id, ids.get(item.clientExternalId()), ids.get(item.supplierExternalId()));
        }
    }

    private void persistEnd(UUID modelId, UUID associationId, AssociationEndData item,
            AssociationEndData opposite, short order, Map<String, UUID> ids, Instant now) {
        UUID id = ids.get(item.externalId());
        insertElement(id, modelId, "PROPERTY", item.roleName(), item.externalId(), now);
        UUID owner = "CLASSIFIER".equals(item.ownership()) ? ids.get(opposite.classifierExternalId()) : null;
        insertProperty(id, owner, item.visibility(),
                new TypedValue("CLASSIFIER", null, item.classifierExternalId()), item.lower(), item.upper(),
                false, false, false, ids);
        jdbc.update("""
                INSERT INTO uml_association_ends(property_id,association_id,navigable,aggregation,ownership,member_order)
                VALUES(?,?,?,?,?,?)
                """, id, associationId, item.navigable(), item.aggregation(), item.ownership(), order);
    }

    private void insertProperty(UUID id, UUID ownerId, String visibility, TypedValue type,
            int lower, Integer upper, boolean isStatic, boolean readOnly, boolean derived, Map<String, UUID> ids) {
        jdbc.update("""
                INSERT INTO uml_properties(element_id,owner_classifier_id,visibility,type_kind,primitive_type,type_classifier_id,
                    lower_bound,upper_bound,is_static,is_read_only,is_derived)
                VALUES(?,?,?,?,?,?,?,?,?,?,?)
                """, id, ownerId, visibility, type.typeKind(), type.primitiveType(),
                resolveNullable(ids, type.classifierExternalId()), lower, upper, isStatic, readOnly, derived);
    }

    private void insertElement(UUID id, UUID modelId, String type, String name,
            String externalId, Instant now) {
        jdbc.update("""
                INSERT INTO uml_elements(id,model_id,element_type,name,documentation,external_xmi_id,created_at,updated_at)
                VALUES(?,?,?,?,NULL,?,?,?)
                """, id, modelId, type, name, externalId, Timestamp.from(now), Timestamp.from(now));
    }

    private Map<String, UUID> allocateIds(XmiImportData data) {
        Map<String, UUID> result = new HashMap<>();
        data.packages().forEach(item -> result.put(item.externalId(), UUID.randomUUID()));
        data.classifiers().forEach(item -> result.put(item.externalId(), UUID.randomUUID()));
        data.properties().forEach(item -> result.put(item.externalId(), UUID.randomUUID()));
        data.operations().forEach(item -> result.put(item.externalId(), UUID.randomUUID()));
        data.parameters().forEach(item -> result.put(item.externalId(), UUID.randomUUID()));
        data.associations().forEach(item -> {
            result.put(item.externalId(), UUID.randomUUID());
            result.put(item.first().externalId(), UUID.randomUUID());
            result.put(item.second().externalId(), UUID.randomUUID());
        });
        data.generalizations().forEach(item -> result.put(item.externalId(), UUID.randomUUID()));
        data.realizations().forEach(item -> result.put(item.externalId(), UUID.randomUUID()));
        return result;
    }

    private void validateIntermediate(XmiImportData data) {
        Set<String> allIds = new HashSet<>();
        List<String> ids = new ArrayList<>();
        data.packages().forEach(item -> ids.add(item.externalId()));
        data.classifiers().forEach(item -> ids.add(item.externalId()));
        data.properties().forEach(item -> ids.add(item.externalId()));
        data.operations().forEach(item -> ids.add(item.externalId()));
        data.parameters().forEach(item -> ids.add(item.externalId()));
        data.associations().forEach(item -> { ids.add(item.externalId()); ids.add(item.first().externalId()); ids.add(item.second().externalId()); });
        data.generalizations().forEach(item -> ids.add(item.externalId()));
        data.realizations().forEach(item -> ids.add(item.externalId()));
        for (String id : ids) if (id == null || !allIds.add(id)) throw error("XMI_ID_DUPLICATED",
                HttpStatus.BAD_REQUEST, "External XMI identifiers must be unique: " + id);

        Set<String> packageIds = externalIds(data.packages().stream().map(PackageData::externalId).toList());
        Set<String> classifierIds = externalIds(data.classifiers().stream().map(ClassifierData::externalId).toList());
        Set<String> operationIds = externalIds(data.operations().stream().map(OperationData::externalId).toList());
        data.packages().forEach(item -> requireOptional(packageIds, item.parentExternalId(), "package parent"));
        data.classifiers().forEach(item -> requireOptional(packageIds, item.packageExternalId(), "classifier package"));
        data.properties().forEach(item -> { require(classifierIds, item.ownerClassifierExternalId(), "property owner"); requireType(item.type(), classifierIds); });
        data.operations().forEach(item -> require(classifierIds, item.ownerClassifierExternalId(), "operation owner"));
        data.parameters().forEach(item -> { require(operationIds, item.operationExternalId(), "parameter operation"); requireType(item.type(), classifierIds); });
        data.associations().forEach(item -> {
            requireOptional(packageIds, item.packageExternalId(), "association package");
            require(classifierIds, item.first().classifierExternalId(), "association end");
            require(classifierIds, item.second().classifierExternalId(), "association end");
        });
        data.generalizations().forEach(item -> { require(classifierIds, item.specificExternalId(), "specific classifier"); require(classifierIds, item.generalExternalId(), "general classifier"); });
        data.realizations().forEach(item -> { require(classifierIds, item.clientExternalId(), "realization client"); require(classifierIds, item.supplierExternalId(), "realization supplier"); });

        Set<String> names = new HashSet<>();
        for (ClassifierData item : data.classifiers()) {
            String key = value(item.packageExternalId()) + ":" + item.name().toLowerCase(Locale.ROOT);
            if (!names.add(key)) throw error("CLASSIFIER_NAME_ALREADY_EXISTS", HttpStatus.CONFLICT,
                    "Duplicate Classifier name in the same UML package: " + item.name());
        }
    }

    private void requireType(TypedValue type, Set<String> classifierIds) {
        if ("CLASSIFIER".equals(type.typeKind())) require(classifierIds, type.classifierExternalId(), "classifier type");
    }
    private void require(Set<String> values, String value, String kind) {
        if (value == null || !values.contains(value)) throw error("XMI_REFERENCE_UNRESOLVED", HttpStatus.BAD_REQUEST,
                "Unresolved " + kind + " reference: " + value);
    }
    private void requireOptional(Set<String> values, String value, String kind) { if (value != null) require(values, value, kind); }
    private Set<String> externalIds(List<String> values) { return new HashSet<>(values); }
    private UUID resolveNullable(Map<String, UUID> ids, String externalId) { return externalId == null ? null : ids.get(externalId); }
    private String value(String value) { return value == null ? "" : value; }
    private String normalizedModelName(String name) {
        String value = name == null ? "Imported model" : name.trim();
        if (value.isBlank() || value.length() > 180) throw error("XMI_MODEL_NAME_INVALID", HttpStatus.BAD_REQUEST,
                "Imported model name must contain between 1 and 180 characters");
        return value;
    }
    private XmiImportException error(String code, HttpStatus status, String message) {
        return new XmiImportException(code, status, message);
    }
}
