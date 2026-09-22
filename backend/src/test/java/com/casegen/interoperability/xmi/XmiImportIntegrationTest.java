package com.casegen.interoperability.xmi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.casegen.auth.domain.User;
import com.casegen.auth.repository.UserRepository;
import com.casegen.modeling.service.ModelSnapshotReader;
import com.casegen.modeling.dto.UpdateDiagramNodeGeometryRequest;
import com.casegen.modeling.service.DiagramService;
import com.casegen.project.domain.Project;
import com.casegen.project.domain.ProjectMember;
import com.casegen.project.domain.ProjectMemberRole;
import com.casegen.project.repository.ProjectMemberRepository;
import com.casegen.project.repository.ProjectRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestConstructor;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = "jdbc:postgresql:.*")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class XmiImportIntegrationTest {
    private final XmiImportService importer;
    private final XmiExportService exporter;
    private final ModelSnapshotReader snapshots;
    private final DiagramService diagrams;
    private final UserRepository users;
    private final ProjectRepository projects;
    private final ProjectMemberRepository members;
    private final JdbcTemplate jdbc;

    XmiImportIntegrationTest(XmiImportService importer, XmiExportService exporter,
            ModelSnapshotReader snapshots, DiagramService diagrams,
            UserRepository users, ProjectRepository projects,
            ProjectMemberRepository members, JdbcTemplate jdbc) {
        this.importer = importer;
        this.exporter = exporter;
        this.snapshots = snapshots;
        this.diagrams = diagrams;
        this.users = users;
        this.projects = projects;
        this.members = members;
        this.jdbc = jdbc;
    }

    @AfterEach
    void clearAuthentication() { SecurityContextHolder.clearContext(); }

    @Test
    void importsRealEaFixturePersistsExternalIdsValidatesAndRoundTrips() throws Exception {
        User owner = user("ea-owner", ProjectMemberRole.OWNER);
        Project sourceProject = project(owner, ProjectMemberRole.OWNER);
        authenticate(owner);
        byte[] fixture = Files.readAllBytes(fixture("samples/enterprise-architect/06-domain-model/domain-model-ea.xmi"));

        XmiImportResponse imported = importer.importModel(sourceProject.getId(), fixture);
        assertEquals(XmiDialect.EA_XMI_1_1_UML_1_3, imported.dialect());
        assertEquals(5, imported.classCount());
        assertEquals(1, imported.interfaceCount());
        assertEquals(3, imported.associationCount());
        assertEquals(1, imported.generalizationCount());
        assertEquals(1, imported.realizationCount());
        assertNotNull(imported.diagram());
        assertEquals("EA_Model - Importado", imported.diagram().name());
        assertEquals(6, imported.diagram().nodeCount());
        assertEquals(5, imported.diagram().edgeCount());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM diagrams WHERE model_id=?",
                Integer.class, imported.modelId()));
        assertEquals(6, jdbc.queryForObject("SELECT count(*) FROM diagram_nodes WHERE diagram_id=?",
                Integer.class, imported.diagram().diagramId()));
        assertEquals(5, jdbc.queryForObject("SELECT count(*) FROM diagram_edges WHERE diagram_id=?",
                Integer.class, imported.diagram().diagramId()));
        assertEquals(6, jdbc.queryForObject("""
                SELECT count(DISTINCT (x,y)) FROM diagram_nodes WHERE diagram_id=?
                """, Integer.class, imported.diagram().diagramId()));
        assertEquals(1L, jdbc.queryForObject("SELECT revision FROM uml_models WHERE id=?",
                Long.class, imported.modelId()));
        assertEquals(11L, jdbc.queryForObject("SELECT revision FROM diagrams WHERE id=?",
                Long.class, imported.diagram().diagramId()));
        assertNotNull(imported.validation().validationId());
        assertTrue(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM uml_elements WHERE model_id=? AND external_xmi_id='EAID_F79345F5_6A27_4ef2_AD15_7F4909B87AC5')
                """, Boolean.class, imported.modelId()));

        byte[] exported = exporter.export(imported.modelId()).content();
        Project roundTripProject = project(owner, ProjectMemberRole.OWNER);
        XmiImportResponse roundTrip = importer.importModel(roundTripProject.getId(), exported);
        var first = snapshots.parse(snapshots.semantic(imported.modelId()));
        var second = snapshots.parse(snapshots.semantic(roundTrip.modelId()));
        SemanticModelComparator.assertEquivalent(first, second);
        for (String collection : List.of("packages", "classifiers", "properties", "operations", "parameters",
                "associations", "associationEnds", "generalizations", "realizations")) {
            assertEquals(first.path(collection).size(), second.path(collection).size(), collection);
        }
    }

    @Test
    void importsLegacyEaFeaturesWithoutXmiIdsAndPersistsResolvedExternalIdentity() {
        User owner = user("legacy-ea-owner", ProjectMemberRole.OWNER);
        Project project = project(owner, ProjectMemberRole.OWNER);
        authenticate(owner);

        XmiImportResponse imported = importer.importModel(project.getId(),
                legacyEaFeatures("Boolean").getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertEquals(XmiDialect.EA_XMI_1_1_UML_1_3, imported.dialect());
        assertEquals(1, imported.propertyCount());
        assertEquals(1, imported.operationCount());
        assertEquals(2, imported.parameterCount());
        assertNotNull(imported.diagram());
        assertEquals(1, imported.diagram().nodeCount());
        assertEquals(0, imported.diagram().edgeCount());
        assertTrue(imported.warnings().stream().anyMatch(warning ->
                warning.code().equals("SYNTHETIC_EXTERNAL_ID")));
        assertTrue(imported.warnings().stream().anyMatch(warning ->
                warning.code().equals("DUPLICATE_RETURN_PARAMETER_NORMALIZED")));
        assertTrue(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM uml_elements
                WHERE model_id=? AND external_xmi_id='ea-guid:{ATTRIBUTE-GUID}')
                """, Boolean.class, imported.modelId()));
        assertTrue(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM uml_elements
                WHERE model_id=? AND external_xmi_id LIKE 'synthetic:xmi:CUSTOMER:PARAMETER:1:return%')
                """, Boolean.class, imported.modelId()));
        assertEquals(1, jdbc.queryForObject("""
                SELECT count(*) FROM uml_parameters parameter
                JOIN uml_elements element ON element.id=parameter.element_id
                WHERE element.model_id=? AND parameter.direction='RETURN'
                """, Integer.class, imported.modelId()));

        Project roundTripProject = project(owner, ProjectMemberRole.OWNER);
        XmiImportResponse roundTrip = importer.importModel(roundTripProject.getId(),
                exporter.export(imported.modelId()).content());
        SemanticModelComparator.assertEquivalent(
                snapshots.parse(snapshots.semantic(imported.modelId())),
                snapshots.parse(snapshots.semantic(roundTrip.modelId())));
        assertEquals(1, roundTrip.operationCount());
        assertEquals(2, roundTrip.parameterCount());

        var importedDiagram = diagrams.getDiagram(imported.diagram().diagramId());
        long modelRevision = jdbc.queryForObject("SELECT revision FROM uml_models WHERE id=?",
                Long.class, imported.modelId());
        var node = importedDiagram.nodes().getFirst();
        diagrams.updateNodeGeometry(importedDiagram.id(), node.id(),
                new UpdateDiagramNodeGeometryRequest(node.x() + 75, node.y() + 40,
                        node.width(), node.height(), node.zIndex()));
        assertEquals(importedDiagram.revision() + 1, diagrams.getDiagram(importedDiagram.id()).revision());
        assertEquals(modelRevision, jdbc.queryForObject("SELECT revision FROM uml_models WHERE id=?",
                Long.class, imported.modelId()));

        diagrams.removeNode(importedDiagram.id(), node.id());
        assertEquals(1, jdbc.queryForObject("""
                SELECT count(*) FROM uml_classifiers classifier
                JOIN uml_elements element ON element.id=classifier.element_id
                WHERE element.model_id=?
                """, Integer.class, imported.modelId()));
    }

    @Test
    void conflictingLegacyEaReturnsAbortBeforePersistingTheModel() {
        User owner = user("legacy-ea-conflict", ProjectMemberRole.OWNER);
        Project project = project(owner, ProjectMemberRole.OWNER);
        authenticate(owner);
        long before = jdbc.queryForObject("SELECT count(*) FROM uml_models WHERE project_id=?",
                Long.class, project.getId());

        XmiImportException failure = assertThrows(XmiImportException.class, () -> importer.importModel(
                project.getId(), legacyEaFeatures("String").getBytes(java.nio.charset.StandardCharsets.UTF_8)));

        assertEquals("AMBIGUOUS_RETURN_PARAMETER", failure.getCode());
        assertEquals(before, jdbc.queryForObject("SELECT count(*) FROM uml_models WHERE project_id=?",
                Long.class, project.getId()));
    }

    @Test
    void importsModelWithoutVisualClassifiersWithoutCreatingAnEmptyDiagram() {
        User owner = user("no-visual-classifier", ProjectMemberRole.OWNER);
        Project project = project(owner, ProjectMemberRole.OWNER);
        authenticate(owner);
        String xml = """
                <?xml version="1.0"?>
                <XMI xmi.version="1.1" xmlns:UML="org.omg.xmi.namespace.UML">
                  <XMI.content><UML:Model xmi.id="MODEL" name="No classifiers">
                    <UML:Namespace.ownedElement><UML:DataType xmi.id="STRING" name="String"/></UML:Namespace.ownedElement>
                  </UML:Model></XMI.content>
                </XMI>
                """;

        XmiImportResponse imported = importer.importModel(project.getId(),
                xml.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertEquals(null, imported.diagram());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM diagrams WHERE model_id=?",
                Integer.class, imported.modelId()));
    }

    @Test
    void importsEaInvoiceModificationAndPreservesItThroughCasegenRoundTrip() {
        User owner = user("invoice-roundtrip", ProjectMemberRole.OWNER);
        Project project = project(owner, ProjectMemberRole.OWNER);
        authenticate(owner);

        XmiImportResponse imported = importer.importModel(project.getId(),
                invoiceEaXmi().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(XmiDialect.EA_XMI_1_1_UML_1_3, imported.dialect());
        assertEquals(2, imported.classCount());
        assertEquals(3, imported.propertyCount());
        assertEquals(1, imported.operationCount());
        assertEquals(1, imported.associationCount());
        assertEquals(1, jdbc.queryForObject("""
                SELECT count(*) FROM uml_parameters parameter
                JOIN uml_elements element ON element.id=parameter.element_id
                WHERE element.model_id=? AND parameter.direction='RETURN'
                    AND parameter.primitive_type='REAL'
                """, Integer.class, imported.modelId()));

        Project reimportProject = project(owner, ProjectMemberRole.OWNER);
        XmiImportResponse reimported = importer.importModel(reimportProject.getId(),
                exporter.export(imported.modelId()).content());
        var result = SemanticModelComparator.compare(
                snapshots.parse(snapshots.semantic(imported.modelId())),
                snapshots.parse(snapshots.semantic(reimported.modelId())),
                (int) imported.warnings().stream()
                        .filter(warning -> warning.code().equals("UNSUPPORTED_XMI_ELEMENT")).count());
        assertTrue(result.equivalent());
        assertEquals("Round-trip supported subset: PASS", result.status());
    }

    @Test
    void persistsInverseAggregationOnWholeWithoutInvertingMultiplicityRoleOrNavigation() {
        User owner = user("orientation-owner", ProjectMemberRole.OWNER);
        Project project = project(owner, ProjectMemberRole.OWNER);
        authenticate(owner);
        XmiImportResponse imported = importer.importModel(project.getId(), orientationXmi().getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertEnd(imported.modelId(), "Supplier", "supplier", "SHARED", 1, 1, false);
        assertEnd(imported.modelId(), "Product", "products", "NONE", 0, null, true);
        assertEnd(imported.modelId(), "PurchaseOrder", "purchaseOrder", "COMPOSITE", 1, 1, false);
        assertEnd(imported.modelId(), "OrderItem", "items", "NONE", 1, null, true);

        byte[] exported = exporter.export(imported.modelId()).content();
        Project reimportProject = project(owner, ProjectMemberRole.OWNER);
        XmiImportResponse reimported = importer.importModel(reimportProject.getId(), exported);
        SemanticModelComparator.assertEquivalent(
                snapshots.parse(snapshots.semantic(imported.modelId())),
                snapshots.parse(snapshots.semantic(reimported.modelId())));
    }

    @Test
    void unresolvedReferenceRollsBackAndViewerCannotImport() {
        User owner = user("rollback-owner", ProjectMemberRole.OWNER);
        Project project = project(owner, ProjectMemberRole.OWNER);
        authenticate(owner);
        long before = jdbc.queryForObject("SELECT count(*) FROM uml_models WHERE project_id=?", Long.class, project.getId());
        String broken = orientationXmi().replace("xmi:idref=\"Product\"", "xmi:idref=\"Missing\"");
        XmiImportException failure = assertThrows(XmiImportException.class,
                () -> importer.importModel(project.getId(), broken.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertEquals("XMI_REFERENCE_UNRESOLVED", failure.getCode());
        assertEquals(before, jdbc.queryForObject("SELECT count(*) FROM uml_models WHERE project_id=?", Long.class, project.getId()));

        User viewer = users.saveAndFlush(new User(UUID.randomUUID() + "@test.local", "hashed", "Viewer"));
        members.saveAndFlush(new ProjectMember(project, viewer, ProjectMemberRole.VIEWER));
        authenticate(viewer);
        assertThrows(AccessDeniedException.class,
                () -> importer.importModel(project.getId(), orientationXmi().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    private void assertEnd(UUID modelId, String classifierName, String role, String aggregation,
            int lower, Integer upper, boolean navigable) {
        var row = jdbc.queryForMap("""
                SELECT role.name AS role, ae.aggregation, p.lower_bound, p.upper_bound, ae.navigable
                FROM uml_association_ends ae
                JOIN uml_properties p ON p.element_id=ae.property_id
                JOIN uml_elements role ON role.id=p.element_id
                JOIN uml_classifiers c ON c.element_id=p.type_classifier_id
                JOIN uml_elements classifier ON classifier.id=c.element_id
                WHERE role.model_id=? AND classifier.name=?
                """, modelId, classifierName);
        assertEquals(role, row.get("role"));
        assertEquals(aggregation, row.get("aggregation"));
        assertEquals(lower, row.get("lower_bound"));
        assertEquals(upper, row.get("upper_bound"));
        assertEquals(navigable, row.get("navigable"));
    }

    private User user(String label, ProjectMemberRole ignored) {
        return users.saveAndFlush(new User(label + "." + UUID.randomUUID() + "@test.local", "hashed", label));
    }

    private Project project(User user, ProjectMemberRole role) {
        Project project = projects.saveAndFlush(new Project("XMI " + UUID.randomUUID(), null));
        members.saveAndFlush(new ProjectMember(project, user, role));
        return project;
    }

    private void authenticate(User user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user.getId().toString(), "", List.of()));
    }

    private Path fixture(String relative) {
        Path fromBackend = Path.of("..").resolve(relative);
        return Files.exists(fromBackend) ? fromBackend : Path.of(relative);
    }

    private String orientationXmi() {
        return """
                <?xml version="1.0"?>
                <xmi:XMI xmlns:xmi="http://www.omg.org/spec/XMI/20131001" xmlns:uml="http://www.omg.org/spec/UML/20161101">
                  <uml:Model xmi:type="uml:Model" xmi:id="model" name="Imported orientation"><packagedElement xmi:type="uml:Package" xmi:id="domain" name="domain">
                    <packagedElement xmi:type="uml:Class" xmi:id="Supplier" name="Supplier"/>
                    <packagedElement xmi:type="uml:Class" xmi:id="Product" name="Product"/>
                    <packagedElement xmi:type="uml:Class" xmi:id="PurchaseOrder" name="PurchaseOrder"/>
                    <packagedElement xmi:type="uml:Class" xmi:id="OrderItem" name="OrderItem"/>
                    <packagedElement xmi:type="uml:Association" xmi:id="a1" name="SupplierProducts" navigableOwnedEnd="s2">
                      <memberEnd xmi:idref="s1"/><memberEnd xmi:idref="s2"/>
                      <ownedEnd xmi:type="uml:Property" xmi:id="s1" name="supplier" visibility="public" aggregation="none"><type xmi:idref="Supplier"/><lowerValue value="1"/><upperValue value="1"/></ownedEnd>
                      <ownedEnd xmi:type="uml:Property" xmi:id="s2" name="products" visibility="public" aggregation="shared"><type xmi:idref="Product"/><lowerValue value="0"/><upperValue value="*"/></ownedEnd>
                    </packagedElement>
                    <packagedElement xmi:type="uml:Association" xmi:id="a2" name="PurchaseOrderItems" navigableOwnedEnd="o2">
                      <memberEnd xmi:idref="o1"/><memberEnd xmi:idref="o2"/>
                      <ownedEnd xmi:type="uml:Property" xmi:id="o1" name="purchaseOrder" visibility="private" aggregation="none"><type xmi:idref="PurchaseOrder"/><lowerValue value="1"/><upperValue value="1"/></ownedEnd>
                      <ownedEnd xmi:type="uml:Property" xmi:id="o2" name="items" visibility="public" aggregation="composite"><type xmi:idref="OrderItem"/><lowerValue value="1"/><upperValue value="*"/></ownedEnd>
                    </packagedElement>
                  </packagedElement></uml:Model>
                </xmi:XMI>
                """;
    }

    private String legacyEaFeatures(String duplicateReturnType) {
        return """
                <?xml version="1.0"?>
                <XMI xmi.version="1.1" xmlns:UML="org.omg.xmi.namespace.UML">
                  <XMI.content><UML:Model xmi.id="MODEL" name="Imported legacy EA"><UML:Namespace.ownedElement>
                    <UML:DataType xmi.id="STRING" name="String"/>
                    <UML:DataType xmi.id="REAL" name="Real"/>
                    <UML:DataType xmi.id="BOOLEAN" name="Boolean"/>
                    <UML:Class xmi.id="CUSTOMER" name="Customer"><UML:Classifier.feature>
                      <UML:Attribute name="email" visibility="private">
                        <UML:StructuralFeature.type><UML:DataType xmi.idref="STRING"/></UML:StructuralFeature.type>
                        <UML:ModelElement.taggedValue><UML:TaggedValue tag="ea_guid" value="{ATTRIBUTE-GUID}"/></UML:ModelElement.taggedValue>
                      </UML:Attribute>
                      <UML:Operation name="process" visibility="public">
                        <UML:ModelElement.taggedValue>
                          <UML:TaggedValue tag="ea_guid" value="{OPERATION-GUID}"/>
                          <UML:TaggedValue tag="type" value="Boolean"/>
                        </UML:ModelElement.taggedValue>
                        <UML:BehavioralFeature.parameter>
                          <UML:Parameter name="value" kind="in">
                            <UML:Parameter.type><UML:DataType xmi.idref="REAL"/></UML:Parameter.type>
                            <UML:ModelElement.taggedValue><UML:TaggedValue tag="ea_guid" value="{PARAMETER-GUID}"/></UML:ModelElement.taggedValue>
                          </UML:Parameter>
                          <UML:Parameter kind="return">
                            <UML:Parameter.type><UML:DataType xmi.idref="BOOLEAN"/></UML:Parameter.type>
                          </UML:Parameter>
                          <UML:Parameter name="DuplicateParam_1" kind="return">
                            <UML:Parameter.type><UML:DataType xmi.idref="%s"/></UML:Parameter.type>
                            <UML:ModelElement.taggedValue><UML:TaggedValue tag="ea_guid" value="{DUPLICATE-RETURN-GUID}"/></UML:ModelElement.taggedValue>
                          </UML:Parameter>
                        </UML:BehavioralFeature.parameter>
                      </UML:Operation>
                    </UML:Classifier.feature></UML:Class>
                  </UML:Namespace.ownedElement></UML:Model></XMI.content>
                </XMI>
                """.formatted(duplicateReturnType.toUpperCase(java.util.Locale.ROOT));
    }

    private String invoiceEaXmi() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <xmi:XMI xmlns:xmi="http://www.omg.org/spec/XMI/20131001"
                         xmlns:uml="http://www.omg.org/spec/UML/20161101">
                  <xmi:Documentation exporter="Enterprise Architect" exporterVersion="6.5"/>
                  <uml:Model xmi:type="uml:Model" xmi:id="invoice-model" name="Commercial model with Invoice">
                    <packagedElement xmi:type="uml:Package" xmi:id="sales-package" name="sales">
                      <packagedElement xmi:type="uml:Class" xmi:id="purchase-order" name="PurchaseOrder" visibility="public"/>
                      <packagedElement xmi:type="uml:Class" xmi:id="invoice" name="Invoice" visibility="public">
                        %s
                        %s
                        %s
                        <ownedOperation xmi:type="uml:Operation" xmi:id="calculate-total" name="calculateTotal" visibility="public">
                          <ownedParameter xmi:type="uml:Parameter" xmi:id="tax" name="tax" direction="in">
                            <type xmi:type="uml:PrimitiveType" href="http://www.omg.org/spec/UML/20161101/PrimitiveTypes.xmi#Real"/>
                            <lowerValue value="1"/><upperValue value="1"/>
                          </ownedParameter>
                          <ownedParameter xmi:type="uml:Parameter" xmi:id="calculated-total" direction="return">
                            <type xmi:type="uml:PrimitiveType" href="http://www.omg.org/spec/UML/20161101/PrimitiveTypes.xmi#Real"/>
                            <lowerValue value="1"/><upperValue value="1"/>
                          </ownedParameter>
                        </ownedOperation>
                      </packagedElement>
                      <packagedElement xmi:type="uml:Association" xmi:id="purchase-order-invoice" name="PurchaseOrderInvoice">
                        <memberEnd xmi:idref="purchase-order-end"/><memberEnd xmi:idref="invoice-end"/>
                        <ownedEnd xmi:type="uml:Property" xmi:id="purchase-order-end" name="purchaseOrder" visibility="public" aggregation="none">
                          <type xmi:idref="purchase-order"/><lowerValue value="1"/><upperValue value="1"/>
                        </ownedEnd>
                        <ownedEnd xmi:type="uml:Property" xmi:id="invoice-end" name="invoice" visibility="public" aggregation="none">
                          <type xmi:idref="invoice"/><lowerValue value="0"/><upperValue value="1"/>
                        </ownedEnd>
                      </packagedElement>
                    </packagedElement>
                  </uml:Model>
                </xmi:XMI>
                """.formatted(invoiceAttribute("invoice-id", "id", "Integer"),
                invoiceAttribute("invoice-number", "number", "String"),
                invoiceAttribute("invoice-total", "total", "Real"));
    }

    private String invoiceAttribute(String id, String name, String type) {
        return """
                <ownedAttribute xmi:type="uml:Property" xmi:id="%s" name="%s" visibility="private">
                  <type xmi:type="uml:PrimitiveType" href="http://www.omg.org/spec/UML/20161101/PrimitiveTypes.xmi#%s"/>
                  <lowerValue value="1"/><upperValue value="1"/>
                </ownedAttribute>
                """.formatted(id, name, type);
    }
}
