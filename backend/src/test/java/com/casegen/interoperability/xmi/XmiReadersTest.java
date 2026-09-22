package com.casegen.interoperability.xmi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class XmiReadersTest {
    private final SecureXmiParser parser = new SecureXmiParser();
    private final XmiDialectDetector detector = new XmiDialectDetector();

    @Test
    void readsRealEnterpriseArchitectFixtureWithFeaturesAndRelationships() throws Exception {
        byte[] bytes = Files.readAllBytes(fixture("samples/enterprise-architect/06-domain-model/domain-model-ea.xmi"));
        var document = parser.parse(bytes);
        assertEquals(XmiDialect.EA_XMI_1_1_UML_1_3, detector.detect(document));
        XmiImportData data = new EnterpriseArchitectXmiReader().read(document);

        assertEquals(5, data.classifiers().stream().filter(value -> value.kind().equals("CLASS")).count());
        assertEquals(1, data.classifiers().stream().filter(value -> value.kind().equals("INTERFACE")).count());
        assertTrue(data.properties().stream().anyMatch(value -> value.name().equals("price")
                && "REAL".equals(value.type().primitiveType())));
        assertTrue(data.operations().stream().anyMatch(value -> value.name().equals("register")));
        assertTrue(data.parameters().stream().anyMatch(value -> value.direction().equals("RETURN")
                && "BOOLEAN".equals(value.type().primitiveType())));
        assertEquals(3, data.associations().size());
        assertEquals(1, data.generalizations().size());
        assertEquals(1, data.realizations().size());
    }

    @Test
    void appliesInverseEaAggregationRuleWithoutMovingOtherEndData() {
        XmiImportData data = new CasegenXmiReader().read(parser.parse(orientationXmi().getBytes(StandardCharsets.UTF_8)));

        assertAssociation(data, "SupplierProducts", "Supplier", "supplier", "SHARED", 1, 1, false);
        assertAssociation(data, "SupplierProducts", "Product", "products", "NONE", 0, null, true);
        assertAssociation(data, "PurchaseOrderItems", "PurchaseOrder", "purchaseOrder", "COMPOSITE", 1, 1, false);
        assertAssociation(data, "PurchaseOrderItems", "OrderItem", "items", "NONE", 1, null, true);
        assertAssociation(data, "CustomerAddresses", "Customer", "customer", "COMPOSITE", 1, 1, true);
        assertAssociation(data, "CustomerAddresses", "Address", "addresses", "NONE", 0, null, false);
        assertAssociation(data, "NormalLink", "Supplier", "source", "NONE", 1, 1, false);
        assertAssociation(data, "NormalLink", "PurchaseOrder", "target", "NONE", 1, 1, false);
    }

    @Test
    void readsLegacyEaXmi11Uml13AndIgnoresUnsupportedElementsWithWarning() {
        String xml = """
                <?xml version="1.0"?>
                <XMI xmi.version="1.1" xmlns:UML="org.omg.xmi.namespace.UML">
                  <XMI.content><UML:Model xmi.id="m1" name="LegacySales"><UML:Namespace.ownedElement>
                    <UML:Package xmi.id="p1" name="domain"><UML:Namespace.ownedElement>
                      <UML:DataType xmi.id="dt1" name="String"/>
                      <UML:Class xmi.id="c1" name="Customer"><UML:Classifier.feature>
                        <UML:Attribute xmi.id="a1" name="email" visibility="private">
                          <UML:StructuralFeature.type><UML:DataType xmi.idref="dt1"/></UML:StructuralFeature.type>
                        </UML:Attribute>
                      </UML:Classifier.feature></UML:Class>
                      <UML:Enumeration xmi.id="e1" name="Status"/>
                    </UML:Namespace.ownedElement></UML:Package>
                  </UML:Namespace.ownedElement></UML:Model></XMI.content>
                </XMI>
                """;
        var document = parser.parse(xml.getBytes(StandardCharsets.UTF_8));
        assertEquals(XmiDialect.EA_XMI_1_1_UML_1_3, detector.detect(document));
        XmiImportData data = new EnterpriseArchitectXmiReader().read(document);
        assertEquals("LegacySales", data.modelName());
        assertEquals("xmi:a1", data.properties().getFirst().externalId());
        assertEquals("STRING", data.properties().getFirst().type().primitiveType());
        assertTrue(data.warnings().stream().anyMatch(value -> value.code().equals("UNSUPPORTED_XMI_ELEMENT")));
    }

    @Test
    void readsLegacyEaFeaturesWithoutXmiIdsUsingTheirOwnEaGuids() {
        XmiImportData data = new EnterpriseArchitectXmiReader().read(
                parser.parse(legacyFeatures(false, false).getBytes(StandardCharsets.UTF_8)));

        assertEquals("ea-guid:{ATTRIBUTE-GUID}", data.properties().getFirst().externalId());
        assertEquals("email", data.properties().getFirst().name());
        assertEquals("STRING", data.properties().getFirst().type().primitiveType());
        assertEquals("ea-guid:{OPERATION-GUID}", data.operations().getFirst().externalId());
        assertEquals("ea-guid:{PARAMETER-GUID}", data.parameters().get(0).externalId());
        assertEquals("IN", data.parameters().get(0).direction());
        assertEquals("REAL", data.parameters().get(0).type().primitiveType());
        assertEquals("ea-guid:{RETURN-GUID}", data.parameters().get(1).externalId());
        assertEquals("RETURN", data.parameters().get(1).direction());
        assertEquals("BOOLEAN", data.parameters().get(1).type().primitiveType());
    }

    @Test
    void createsDeterministicSyntheticFeatureIdAndWarningWhenEaGuidIsAbsent() {
        byte[] xml = legacyFeatures(true, false).getBytes(StandardCharsets.UTF_8);
        XmiImportData first = new EnterpriseArchitectXmiReader().read(parser.parse(xml));
        XmiImportData second = new EnterpriseArchitectXmiReader().read(parser.parse(xml));

        String firstId = first.properties().getFirst().externalId();
        assertEquals(firstId, second.properties().getFirst().externalId());
        assertTrue(firstId.startsWith("synthetic:xmi:CUSTOMER:ATTRIBUTE:0:email"));
        assertTrue(first.warnings().stream().anyMatch(value -> value.code().equals("SYNTHETIC_EXTERNAL_ID")
                && value.externalId().equals(firstId)));
    }

    @Test
    void rejectsDuplicatedEaGuidInsteadOfMergingElements() {
        XmiImportException failure = assertThrows(XmiImportException.class, () -> new EnterpriseArchitectXmiReader()
                .read(parser.parse(legacyFeatures(false, true).getBytes(StandardCharsets.UTF_8))));

        assertEquals("EA_GUID_DUPLICATED", failure.getCode());
    }

    @Test
    void normalizesEquivalentDuplicateReturnsUsingEaPreferenceAndMetadata() {
        String duplicateNamed = legacyParameter("DuplicateParam_1", "return", "Boolean", "{DUPLICATE-GUID}");
        String generatedReturn = legacyParameter(null, "return", "Boolean", "{RETURNID-PROCESS}");
        XmiImportData data = readLegacyOperation(null, duplicateNamed + generatedReturn);

        assertEquals(1, data.parameters().size());
        assertEquals("ea-guid:{RETURNID-PROCESS}", data.parameters().getFirst().externalId());
        var warning = data.warnings().stream()
                .filter(value -> value.code().equals("DUPLICATE_RETURN_PARAMETER_NORMALIZED"))
                .findFirst().orElseThrow();
        assertEquals("process", warning.metadata().get("operation"));
        assertEquals("ea-guid:{RETURNID-PROCESS}", warning.metadata().get("retainedExternalId"));
        assertEquals("BOOLEAN", warning.metadata().get("returnType"));
        assertEquals(java.util.List.of("ea-guid:{DUPLICATE-GUID}"),
                warning.metadata().get("discardedExternalIds"));
    }

    @Test
    void operationTaggedTypeAndEquivalentExplicitReturnProduceOneReturn() {
        XmiImportData data = readLegacyOperation("Boolean",
                legacyParameter(null, "return", "Boolean", "{RETURNID-PROCESS}"));

        assertEquals(1, data.parameters().size());
        assertEquals("BOOLEAN", data.parameters().getFirst().type().primitiveType());
    }

    @Test
    void operationTaggedTypeCreatesReturnOnlyWhenExplicitReturnIsAbsent() {
        XmiImportData data = readLegacyOperation("Boolean", "");

        assertEquals(1, data.parameters().size());
        assertEquals("RETURN", data.parameters().getFirst().direction());
        assertEquals("BOOLEAN", data.parameters().getFirst().type().primitiveType());
        assertTrue(data.parameters().getFirst().externalId().startsWith(
                "synthetic:ea-guid:{OPERATION-GUID}:PARAMETER_RETURN:0:return"));
    }

    @Test
    void rejectsConflictingExplicitReturnTypesBeforePersistence() {
        String booleanReturn = legacyParameter(null, "return", "Boolean", "{RETURNID-BOOLEAN}");
        String stringReturn = legacyParameter("DuplicateParam_1", "return", "String", "{RETURNID-STRING}");

        XmiImportException failure = assertThrows(XmiImportException.class,
                () -> readLegacyOperation(null, booleanReturn + stringReturn));

        assertEquals("AMBIGUOUS_RETURN_PARAMETER", failure.getCode());
        assertTrue(failure.getMessage().contains("process"));
        assertTrue(failure.getMessage().contains("BOOLEAN"));
        assertTrue(failure.getMessage().contains("STRING"));
    }

    @Test
    void doesNotDeduplicateNormalParametersThatHaveTheSameType() {
        String first = legacyParameter("first", "in", "String", "{FIRST-GUID}");
        String second = legacyParameter("second", "in", "String", "{SECOND-GUID}");
        XmiImportData data = readLegacyOperation(null, first + second);

        assertEquals(2, data.parameters().size());
        assertTrue(data.parameters().stream().allMatch(value -> value.direction().equals("IN")));
    }

    @Test
    void importsEa11RealizationNavigationAndSkipsOnlyTheTechnicalRootClass() {
        XmiImportData data = new EnterpriseArchitectXmiReader().read(parser.parse(
                legacyEaCompatibilityXmi().getBytes(StandardCharsets.UTF_8)));

        assertEquals(7, data.classifiers().stream().filter(value -> value.kind().equals("CLASS")).count());
        assertEquals(1, data.classifiers().stream().filter(value -> value.kind().equals("INTERFACE")).count());
        assertTrue(data.classifiers().stream().noneMatch(value -> value.name().equals("EARootClass")));
        assertTrue(data.warnings().stream().anyMatch(value ->
                value.code().equals("EA_INFRASTRUCTURE_ELEMENT_SKIPPED")));

        assertEquals(1, data.realizations().size());
        assertEquals("xmi:PAYMENT", data.realizations().getFirst().clientExternalId());
        assertEquals("xmi:PAYABLE", data.realizations().getFirst().supplierExternalId());
        assertEquals("xmi:REALIZES", data.realizations().getFirst().externalId());
        assertEquals(1, data.generalizations().size());

        assertLegacyAssociation(data, "Normal", "Payment", "NONE", false);
        assertLegacyAssociation(data, "Normal", "Payable", "NONE", false);
        assertLegacyAssociation(data, "Aggregation", "Supplier", "SHARED", false);
        assertLegacyAssociation(data, "Aggregation", "Product", "NONE", false);
        assertLegacyAssociation(data, "Composition", "Order", "COMPOSITE", false);
        assertLegacyAssociation(data, "Composition", "Item", "NONE", false);
    }

    @Test
    void eaNavigationStyleOverridesAssociationEndFlagAndFallsBackInOrder() {
        String xml = legacyEaCompatibilityXmi()
                .replaceFirst("Navigable=Unspecified;", "Navigable=Navigable;")
                .replaceFirst("Navigable=Unspecified;", "Navigable=Non-Navigable;");
        XmiImportData data = new EnterpriseArchitectXmiReader().read(
                parser.parse(xml.getBytes(StandardCharsets.UTF_8)));

        assertLegacyAssociation(data, "Normal", "Payment", "NONE", true);
        assertLegacyAssociation(data, "Normal", "Payable", "NONE", false);
    }

    @Test
    void keepsAUserClassNamedEaRootClassWhenItIsInsideARealPackage() {
        String xml = legacyEaCompatibilityXmi().replace(
                "<UML:Class xmi.id=\"PAYMENT\" name=\"Payment\"/>",
                "<UML:Class xmi.id=\"PAYMENT\" name=\"Payment\"/>"
                        + "<UML:Class xmi.id=\"USER_ROOT\" name=\"EARootClass\" isRoot=\"true\"/>");
        XmiImportData data = new EnterpriseArchitectXmiReader().read(
                parser.parse(xml.getBytes(StandardCharsets.UTF_8)));

        assertEquals(1, data.classifiers().stream().filter(value -> value.name().equals("EARootClass")).count());
        assertTrue(data.classifiers().stream().anyMatch(value -> value.externalId().equals("xmi:USER_ROOT")));
    }

    @Test
    void rejectsEaRealizationWhoseClientAndSupplierKindsAreInvalid() {
        String invalid = legacyEaCompatibilityXmi()
                .replace("client=\"PAYMENT\" supplier=\"PAYABLE\"",
                        "client=\"PAYABLE\" supplier=\"PAYMENT\"");

        XmiImportException failure = assertThrows(XmiImportException.class, () ->
                new EnterpriseArchitectXmiReader().read(parser.parse(invalid.getBytes(StandardCharsets.UTF_8))));

        assertEquals("XMI_REALIZATION_CLIENT_INVALID", failure.getCode());
    }

    @Test
    void rejectsExternalEntityPayloadsAndOversizedFiles() {
        String xxe = """
                <?xml version="1.0"?>
                <!DOCTYPE model [<!ENTITY secret SYSTEM "file:///casegen-secret">]>
                <xmi:XMI xmlns:xmi="urn:xmi"><Model name="&secret;"/></xmi:XMI>
                """;
        XmiImportException unsafe = assertThrows(XmiImportException.class,
                () -> parser.parse(xxe.getBytes(StandardCharsets.UTF_8)));
        assertEquals("XMI_XML_MALFORMED", unsafe.getCode());
        byte[] oversized = new byte[SecureXmiParser.MAX_FILE_BYTES + 1];
        XmiImportException tooLarge = assertThrows(XmiImportException.class, () -> parser.parse(oversized));
        assertEquals("XMI_FILE_TOO_LARGE", tooLarge.getCode());
    }

    private void assertAssociation(XmiImportData data, String associationName, String classifier,
            String role, String aggregation, int lower, Integer upper, boolean navigable) {
        var association = data.associations().stream().filter(value -> associationName.equals(value.name()))
                .findFirst().orElseThrow();
        var end = java.util.stream.Stream.of(association.first(), association.second())
                .filter(value -> classifier.equals(value.classifierExternalId())).findFirst().orElseThrow();
        assertEquals(role, end.roleName());
        assertEquals(aggregation, end.aggregation());
        assertEquals(lower, end.lower());
        assertEquals(upper, end.upper());
        assertEquals(navigable, end.navigable());
    }

    private String orientationXmi() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <xmi:XMI xmlns:xmi="http://www.omg.org/spec/XMI/20131001" xmlns:uml="http://www.omg.org/spec/UML/20161101">
                  <uml:Model xmi:type="uml:Model" xmi:id="model" name="Orientation">
                    <packagedElement xmi:type="uml:Package" xmi:id="domain" name="domain">
                      <packagedElement xmi:type="uml:Class" xmi:id="Supplier" name="Supplier"/>
                      <packagedElement xmi:type="uml:Class" xmi:id="Product" name="Product"/>
                      <packagedElement xmi:type="uml:Class" xmi:id="PurchaseOrder" name="PurchaseOrder"/>
                      <packagedElement xmi:type="uml:Class" xmi:id="OrderItem" name="OrderItem"/>
                      <packagedElement xmi:type="uml:Class" xmi:id="Customer" name="Customer"/>
                      <packagedElement xmi:type="uml:Class" xmi:id="Address" name="Address"/>
                      %s
                      %s
                      %s
                      %s
                    </packagedElement>
                  </uml:Model>
                </xmi:XMI>
                """.formatted(
                association("a1", "SupplierProducts", "s1", "Supplier", "supplier", "1", "1", "none", false,
                        "s2", "Product", "products", "0", "*", "shared", true),
                association("a2", "PurchaseOrderItems", "o1", "PurchaseOrder", "purchaseOrder", "1", "1", "none", false,
                        "o2", "OrderItem", "items", "1", "*", "composite", true),
                association("a3", "CustomerAddresses", "c1", "Customer", "customer", "1", "1", "none", true,
                        "c2", "Address", "addresses", "0", "*", "composite", false),
                association("a4", "NormalLink", "n1", "Supplier", "source", "1", "1", "none", false,
                        "n2", "PurchaseOrder", "target", "1", "1", "none", false));
    }

    private String association(String id, String name,
            String firstId, String firstType, String firstRole, String firstLower, String firstUpper,
            String firstAggregation, boolean firstNavigable,
            String secondId, String secondType, String secondRole, String secondLower, String secondUpper,
            String secondAggregation, boolean secondNavigable) {
        String navigable = java.util.stream.Stream.of(
                firstNavigable ? firstId : "", secondNavigable ? secondId : "")
                .filter(value -> !value.isBlank()).collect(java.util.stream.Collectors.joining(" "));
        return """
                <packagedElement xmi:type="uml:Association" xmi:id="%s" name="%s" navigableOwnedEnd="%s">
                  <memberEnd xmi:idref="%s"/><memberEnd xmi:idref="%s"/>
                  %s%s
                </packagedElement>
                """.formatted(id, name, navigable, firstId, secondId,
                end(firstId, firstType, firstRole, firstLower, firstUpper, firstAggregation),
                end(secondId, secondType, secondRole, secondLower, secondUpper, secondAggregation));
    }

    private String legacyFeatures(boolean attributeWithoutGuid, boolean duplicatedGuid) {
        String attributeGuid = attributeWithoutGuid ? "" : taggedGuid("{ATTRIBUTE-GUID}");
        String returnGuid = duplicatedGuid ? "{PARAMETER-GUID}" : "{RETURN-GUID}";
        return """
                <?xml version="1.0"?>
                <XMI xmi.version="1.1" xmlns:UML="org.omg.xmi.namespace.UML">
                  <XMI.content><UML:Model xmi.id="MODEL" name="LegacyFeatures"><UML:Namespace.ownedElement>
                    <UML:DataType xmi.id="STRING" name="String"/>
                    <UML:DataType xmi.id="REAL" name="Real"/>
                    <UML:DataType xmi.id="BOOLEAN" name="Boolean"/>
                    <UML:Class xmi.id="CUSTOMER" name="Customer"><UML:Classifier.feature>
                      <UML:Attribute name="email" visibility="private">
                        <UML:StructuralFeature.type><UML:DataType xmi.idref="STRING"/></UML:StructuralFeature.type>
                        %s
                      </UML:Attribute>
                      <UML:Operation name="process" visibility="public">
                        %s
                        <UML:BehavioralFeature.parameter>
                          <UML:Parameter name="value" kind="in">
                            <UML:Parameter.type><UML:DataType xmi.idref="REAL"/></UML:Parameter.type>
                            %s
                          </UML:Parameter>
                          <UML:Parameter name="return" kind="return">
                            <UML:Parameter.type><UML:DataType xmi.idref="BOOLEAN"/></UML:Parameter.type>
                            %s
                          </UML:Parameter>
                        </UML:BehavioralFeature.parameter>
                      </UML:Operation>
                    </UML:Classifier.feature></UML:Class>
                  </UML:Namespace.ownedElement></UML:Model></XMI.content>
                </XMI>
                """.formatted(attributeGuid, taggedGuid("{OPERATION-GUID}"),
                taggedGuid("{PARAMETER-GUID}"), taggedGuid(returnGuid));
    }

    private String taggedGuid(String guid) {
        return """
                <UML:ModelElement.taggedValue>
                  <UML:TaggedValue tag="ea_guid" value="%s"/>
                </UML:ModelElement.taggedValue>
                """.formatted(guid);
    }

    private XmiImportData readLegacyOperation(String taggedReturnType, String parameterXml) {
        String operationType = taggedReturnType == null ? ""
                : "<UML:TaggedValue tag=\"type\" value=\"" + taggedReturnType + "\"/>";
        String xml = """
                <?xml version="1.0"?>
                <XMI xmi.version="1.1" xmlns:UML="org.omg.xmi.namespace.UML">
                  <XMI.content><UML:Model xmi.id="MODEL" name="LegacyReturn"><UML:Namespace.ownedElement>
                    <UML:DataType xmi.id="STRING" name="String"/>
                    <UML:DataType xmi.id="BOOLEAN" name="Boolean"/>
                    <UML:Class xmi.id="PAYABLE" name="Payable"><UML:Classifier.feature>
                      <UML:Operation name="process" visibility="public">
                        <UML:ModelElement.taggedValue>
                          <UML:TaggedValue tag="ea_guid" value="{OPERATION-GUID}"/>
                          %s
                        </UML:ModelElement.taggedValue>
                        <UML:BehavioralFeature.parameter>%s</UML:BehavioralFeature.parameter>
                      </UML:Operation>
                    </UML:Classifier.feature></UML:Class>
                  </UML:Namespace.ownedElement></UML:Model></XMI.content>
                </XMI>
                """.formatted(operationType, parameterXml);
        return new EnterpriseArchitectXmiReader().read(parser.parse(xml.getBytes(StandardCharsets.UTF_8)));
    }

    private String legacyParameter(String name, String kind, String type, String guid) {
        String nameAttribute = name == null ? "" : " name=\"" + name + "\"";
        return """
                <UML:Parameter%s kind="%s">
                  <UML:Parameter.type><UML:DataType xmi.idref="%s"/></UML:Parameter.type>
                  <UML:ModelElement.taggedValue><UML:TaggedValue tag="ea_guid" value="%s"/></UML:ModelElement.taggedValue>
                </UML:Parameter>
                """.formatted(nameAttribute, kind, type.toUpperCase(java.util.Locale.ROOT), guid);
    }

    private void assertLegacyAssociation(XmiImportData data, String associationName, String classifierName,
            String aggregation, boolean navigable) {
        String classifierId = data.classifiers().stream().filter(value -> value.name().equals(classifierName))
                .map(XmiImportData.ClassifierData::externalId).findFirst().orElseThrow();
        var association = data.associations().stream().filter(value -> associationName.equals(value.name()))
                .findFirst().orElseThrow();
        var end = java.util.stream.Stream.of(association.first(), association.second())
                .filter(value -> classifierId.equals(value.classifierExternalId())).findFirst().orElseThrow();
        assertEquals(aggregation, end.aggregation());
        assertEquals(navigable, end.navigable());
    }

    private String legacyEaCompatibilityXmi() {
        return """
                <?xml version="1.0"?>
                <XMI xmi.version="1.1" xmlns:UML="org.omg.xmi.namespace.UML">
                  <XMI.content><UML:Model xmi.id="MODEL" name="EA Compatibility"><UML:Namespace.ownedElement>
                    <UML:Class xmi.id="EAID_11111111_5487_4080_A7F4_41526CB0AA00" name="EARootClass" isRoot="true"/>
                    <UML:Package xmi.id="DOMAIN" name="domain"><UML:Namespace.ownedElement>
                      <UML:Class xmi.id="PAYMENT" name="Payment"/>
                      <UML:Interface xmi.id="PAYABLE" name="Payable"/>
                      <UML:Class xmi.id="SUPPLIER" name="Supplier"/>
                      <UML:Class xmi.id="PRODUCT" name="Product"/>
                      <UML:Class xmi.id="ORDER" name="Order"/>
                      <UML:Class xmi.id="ITEM" name="Item"/>
                      <UML:Class xmi.id="BASE" name="Base"/>
                      <UML:Class xmi.id="DERIVED" name="Derived"/>
                      %s
                      %s
                      %s
                      <UML:Generalization xmi.id="GENERALIZES" child="DERIVED" parent="BASE"/>
                      <UML:Dependency xmi.id="REALIZES" client="PAYMENT" supplier="PAYABLE">
                        <UML:ModelElement.taggedValue>
                          <UML:TaggedValue tag="ea_type" value="Realisation"/>
                        </UML:ModelElement.taggedValue>
                      </UML:Dependency>
                    </UML:Namespace.ownedElement></UML:Package>
                  </UML:Namespace.ownedElement></UML:Model></XMI.content>
                </XMI>
                """.formatted(
                legacyAssociation("NORMAL", "Normal", "PAYMENT", "PAYABLE", "none"),
                legacyAssociation("AGGREGATION", "Aggregation", "SUPPLIER", "PRODUCT", "shared"),
                legacyAssociation("COMPOSITION", "Composition", "ORDER", "ITEM", "composite"));
    }

    private String legacyAssociation(String id, String name, String source, String target,
            String targetAggregation) {
        return """
                <UML:Association xmi.id="%s" name="%s" source="%s" target="%s">
                  <UML:ModelElement.taggedValue>
                    <UML:TaggedValue tag="sourcestyle" value="LineColor=-1;Navigable=Unspecified;"/>
                    <UML:TaggedValue tag="deststyle" value="LineColor=-1;Navigable=Unspecified;"/>
                  </UML:ModelElement.taggedValue>
                  <UML:Association.connection>
                    <UML:AssociationEnd xmi.id="%s_SOURCE" type="%s" name="source" multiplicity="1" aggregation="none" isNavigable="true"/>
                    <UML:AssociationEnd xmi.id="%s_TARGET" type="%s" name="target" multiplicity="0..*" aggregation="%s" isNavigable="true"/>
                  </UML:Association.connection>
                </UML:Association>
                """.formatted(id, name, source, target, id, source, id, target, targetAggregation);
    }

    private String end(String id, String type, String role, String lower, String upper, String aggregation) {
        return """
                <ownedEnd xmi:type="uml:Property" xmi:id="%s" name="%s" visibility="public" aggregation="%s">
                  <type xmi:idref="%s"/><lowerValue value="%s"/><upperValue value="%s"/>
                </ownedEnd>
                """.formatted(id, role, aggregation, type, lower, upper);
    }

    private Path fixture(String relative) {
        Path fromBackend = Path.of("..").resolve(relative);
        return Files.exists(fromBackend) ? fromBackend : Path.of(relative);
    }
}
