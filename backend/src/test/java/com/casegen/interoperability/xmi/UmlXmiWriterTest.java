package com.casegen.interoperability.xmi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import org.xml.sax.InputSource;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

class UmlXmiWriterTest {
    private final JsonMapper json = JsonMapper.builder().build();
    private final UmlXmiWriter writer = new UmlXmiWriter();

    @Test
    void exportsComplexModelAsDeterministicWellFormedXmi() throws Exception {
        ObjectNode snapshot = complexModel();

        String first = writer.write(snapshot);
        String second = writer.write(snapshot);
        assertEquals(first, second);

        var document = parse(first);
        var xpath = XPathFactory.newInstance().newXPath();
        assertEquals("Commerce", xpath.evaluate("/*[local-name()='XMI']/*[local-name()='Model']/@name", document));
        assertEquals(4d, (Double) xpath.evaluate("count(//*[@*[local-name()='type']='uml:Class'])", document,
                XPathConstants.NUMBER));
        assertEquals(1d, (Double) xpath.evaluate("count(//*[@*[local-name()='type']='uml:Interface'])", document,
                XPathConstants.NUMBER));
        assertEquals(5d, (Double) xpath.evaluate("count(//*[@*[local-name()='type']='uml:Association'])", document,
                XPathConstants.NUMBER));
        assertEquals(0d, (Double) xpath.evaluate("count(//*[@name='UMLPrimitiveTypes'])", document,
                XPathConstants.NUMBER));
        assertPrimitiveHref(xpath, document, "city", "String");
        assertPrimitiveHref(xpath, document, "id", "Integer");
        assertPrimitiveHref(xpath, document, "active", "Boolean");
        assertPrimitiveHref(xpath, document, "ratio", "Real");
        assertPrimitiveHref(xpath, document, "capacity", "UnlimitedNatural");
        assertEquals("private", xpath.evaluate("//*[@name='city']/@visibility", document));
        assertEquals("0", xpath.evaluate("//*[@name='city']/lowerValue/@value", document));
        assertEquals("1", xpath.evaluate("//*[@name='city']/upperValue/@value", document));
        assertEquals("Real", primitiveName(xpath, document,
                "//*[@name='process']/ownedParameter[@name='value']"));
        assertEquals("in", xpath.evaluate("//*[@name='process']/ownedParameter[@name='value']/@direction", document));
        assertEquals("return", xpath.evaluate("//*[@name='process']/ownedParameter[@name='return']/@direction", document));
        assertEquals("Boolean", primitiveName(xpath, document,
                "//*[@name='process']/ownedParameter[@name='return']"));
        String categoryId = xpath.evaluate("//*[@name='Category']/@*[local-name()='id']", document);
        assertEquals(categoryId, xpath.evaluate("//*[@name='category']/type/@*[local-name()='idref']", document));
        assertEquals("none", xpath.evaluate("//*[@name='team']/@aggregation", document));
        assertEquals("shared", xpath.evaluate("//*[@name='members']/@aggregation", document));
        assertEquals("none", xpath.evaluate("//*[@name='order']/@aggregation", document));
        assertEquals("composite", xpath.evaluate("//*[@name='parts']/@aggregation", document));
        assertEquals("0", xpath.evaluate(
                "//*[@*[local-name()='type']='uml:Association'][@name='CustomerSale']/ownedEnd[@name='sales']/lowerValue/@value",
                document));
        assertEquals("*", xpath.evaluate(
                "//*[@*[local-name()='type']='uml:Association'][@name='CustomerSale']/ownedEnd[@name='sales']/upperValue/@value",
                document));
        assertEquals(1d, (Double) xpath.evaluate("count(//generalization)", document, XPathConstants.NUMBER));
        assertEquals(xpath.evaluate("//*[@name='Person']/@*[local-name()='id']", document),
                xpath.evaluate("//*[@name='Payment']/generalization/@general", document));
        assertEquals(1d, (Double) xpath.evaluate(
                "count(//*[@*[local-name()='type']='uml:Realization'])", document, XPathConstants.NUMBER));
        assertEquals(xpath.evaluate("//*[@name='Payment']/@*[local-name()='id']", document),
                xpath.evaluate("//*[@*[local-name()='type']='uml:Realization']/@client", document));
        assertEquals(xpath.evaluate("//*[@name='Payable']/@*[local-name()='id']", document),
                xpath.evaluate("//*[@*[local-name()='type']='uml:Realization']/@supplier", document));
        assertTrue(first.contains("navigableOwnedEnd="));
        assertTrue(first.contains("casegen:internalId="));
    }

    @Test
    void casegenWriterAndReaderPreserveTheCompleteSupportedSemanticSubset() {
        ObjectNode original = complexModel();
        String xmi = writer.write(original);

        XmiImportData imported = new CasegenXmiReader().read(
                new SecureXmiParser().parse(xmi.getBytes(StandardCharsets.UTF_8)));

        SemanticModelComparator.assertEquivalent(original, imported);
    }

    @Test
    void casegenRoundTripPreservesAggregationCompositionEndsAndNavigability() {
        ObjectNode original = aggregationOrientationModel();
        String xmi = writer.write(original);

        XmiImportData imported = new CasegenXmiReader().read(
                new SecureXmiParser().parse(xmi.getBytes(StandardCharsets.UTF_8)));

        SemanticModelComparator.assertEquivalent(original, imported);
    }

    @Test
    void rejectsAssociationWithoutExactlyTwoEnds() {
        ObjectNode snapshot = complexModel();
        String removedProperty = snapshot.path("associationEnds").get(0).path("property_id").asText();
        ((ArrayNode) snapshot.path("associationEnds")).remove(0);
        removeById((ArrayNode) snapshot.path("properties"), "element_id", removedProperty);
        removeById((ArrayNode) snapshot.path("elements"), "id", removedProperty);
        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> writer.write(snapshot));
        assertTrue(error.getReason().contains("exactly two ends"));
    }

    @Test
    void followsEnterpriseArchitectReferenceFixturesForTypesOperationsAndRealization() throws Exception {
        var eaOperation = parse(Files.readString(fixture(
                "samples/enterprise-architect/05-operation/operation-standard.xmi")));
        var eaInterface = parse(Files.readString(fixture(
                "samples/enterprise-architect/04-interface/interface-standard.xmi")));
        var generated = parse(writer.write(complexModel()));
        var xpath = XPathFactory.newInstance().newXPath();

        String eaPrimitiveHref = xpath.evaluate("//*[@name='email']/type/@href", eaOperation);
        assertEquals(UmlXmiWriter.UML_PRIMITIVE_TYPES + "#String", eaPrimitiveHref);
        assertEquals("return", xpath.evaluate("//*[@name='register']/ownedParameter[@direction='return']/@direction",
                eaOperation));
        assertEquals("uml:Realization", xpath.evaluate(
                "//*[@*[local-name()='type']='uml:Realization']/@*[local-name()='type']", eaInterface));

        assertEquals(eaPrimitiveHref, xpath.evaluate("//*[@name='city']/type/@href", generated));
        assertEquals("return", xpath.evaluate("//*[@name='process']/ownedParameter[@name='return']/@direction",
                generated));
        assertEquals("uml:Realization", xpath.evaluate(
                "//*[@*[local-name()='type']='uml:Realization']/@*[local-name()='type']", generated));
    }

    @Test
    void projectsAggregationToTheOppositeEaTypedEndWithoutInvertingEndData() throws Exception {
        ObjectNode snapshot = aggregationOrientationModel();
        String first = writer.write(snapshot);
        assertEquals(first, writer.write(snapshot));
        var document = parse(first);
        var xpath = XPathFactory.newInstance().newXPath();

        assertAssociationEnd(xpath, document, "SupplierProducts", "Supplier", "supplier",
                "none", "1", "1", "false");
        assertAssociationEnd(xpath, document, "SupplierProducts", "Product", "products",
                "shared", "0", "*", "true");
        assertAssociationEnd(xpath, document, "PurchaseOrderItems", "PurchaseOrder", "purchaseOrder",
                "none", "1", "1", "false");
        assertAssociationEnd(xpath, document, "PurchaseOrderItems", "OrderItem", "items",
                "composite", "1", "*", "true");
        assertAssociationEnd(xpath, document, "CustomerAddresses", "Customer", "customer",
                "none", "1", "1", "true");
        assertAssociationEnd(xpath, document, "CustomerAddresses", "Address", "addresses",
                "composite", "0", "*", "false");

        assertStandardNavigability(xpath, document, "SupplierProducts", "Supplier", false);
        assertStandardNavigability(xpath, document, "SupplierProducts", "Product", true);
        assertStandardNavigability(xpath, document, "PurchaseOrderItems", "PurchaseOrder", false);
        assertStandardNavigability(xpath, document, "PurchaseOrderItems", "OrderItem", true);
        assertStandardNavigability(xpath, document, "CustomerAddresses", "Customer", true);
        assertStandardNavigability(xpath, document, "CustomerAddresses", "Address", false);

        assertEquals(0d, (Double) xpath.evaluate(
                "count(//*[@*[local-name()='type']='uml:Association'][@name='NormalLink']/ownedEnd[@aggregation!='none'])",
                document, XPathConstants.NUMBER));
    }

    private ObjectNode complexModel() {
        ObjectNode s = json.createObjectNode();
        String model = id(1);
        s.putObject("model").put("id", model).put("name", "Commerce").put("uml_version", "2.5.1");
        for (String key : new String[]{"elements", "packages", "classifiers", "properties", "operations",
                "parameters", "associations", "associationEnds", "generalizations", "realizations"}) s.putArray(key);

        String domain = id(2);
        element(s, domain, "PACKAGE", "sales").put("external_xmi_id", "sales_package");
        row(s, "packages").put("element_id", domain).putNull("parent_package_id");
        String customer = classifier(s, 10, "Payment", "CLASS", domain);
        String sale = classifier(s, 11, "Sale", "CLASS", domain);
        String category = classifier(s, 12, "Category", "CLASS", domain);
        String person = classifier(s, 13, "Person", "CLASS", domain);
        String identifiable = classifier(s, 14, "Payable", "INTERFACE", domain);

        property(s, 20, "city", customer, "PRIVATE", "PRIMITIVE", "STRING", null, 0, 1);
        property(s, 25, "id", customer, "PRIVATE", "PRIMITIVE", "INTEGER", null, 1, 1);
        property(s, 26, "active", customer, "PRIVATE", "PRIMITIVE", "BOOLEAN", null, 1, 1);
        property(s, 27, "ratio", customer, "PRIVATE", "PRIMITIVE", "REAL", null, 1, 1);
        property(s, 28, "capacity", customer, "PRIVATE", "PRIMITIVE", "UNLIMITED_NATURAL", null, 0, 1);
        property(s, 21, "category", sale, "PUBLIC", "CLASSIFIER", null, category, 1, 1);
        String operation = id(22);
        element(s, operation, "OPERATION", "process");
        row(s, "operations").put("element_id", operation).put("owner_classifier_id", identifiable)
                .put("visibility", "PUBLIC").put("is_abstract", true).put("is_static", false).put("is_query", false);
        parameter(s, 23, "value", operation, "IN", "REAL", 0);
        parameter(s, 24, "return", operation, "RETURN", "BOOLEAN", 1);

        association(s, 30, "CustomerSale", domain, customer, "customer", 1, 1, "NONE", true,
                sale, "sales", 0, null, "NONE", true);
        association(s, 33, "CustomerProfile", domain, customer, "customer", 1, 1, "NONE", false,
                person, "profile", 1, 1, "NONE", true);
        association(s, 36, "SaleCategory", domain, sale, "sales", 0, null, "NONE", true,
                category, "categories", 0, null, "NONE", true);
        association(s, 39, "TeamMembers", domain, customer, "team", 1, 1, "SHARED", false,
                person, "members", 0, null, "NONE", true);
        association(s, 42, "OrderParts", domain, sale, "order", 1, 1, "COMPOSITE", false,
                category, "parts", 1, null, "NONE", true);

        String generalization = id(50);
        element(s, generalization, "GENERALIZATION", null);
        row(s, "generalizations").put("element_id", generalization).put("specific_classifier_id", customer)
                .put("general_classifier_id", person).put("is_substitutable", true);
        String realization = id(51);
        element(s, realization, "REALIZATION", null);
        row(s, "realizations").put("element_id", realization).put("client_classifier_id", customer)
                .put("supplier_classifier_id", identifiable);
        return s;
    }

    private ObjectNode aggregationOrientationModel() {
        ObjectNode s = json.createObjectNode();
        s.putObject("model").put("id", id(100)).put("name", "Aggregation Orientation")
                .put("uml_version", "2.5.1");
        for (String key : new String[]{"elements", "packages", "classifiers", "properties", "operations",
                "parameters", "associations", "associationEnds", "generalizations", "realizations"}) s.putArray(key);
        String packageId = id(101);
        element(s, packageId, "PACKAGE", "domain");
        row(s, "packages").put("element_id", packageId).putNull("parent_package_id");
        String supplier = classifier(s, 102, "Supplier", "CLASS", packageId);
        String product = classifier(s, 103, "Product", "CLASS", packageId);
        String purchaseOrder = classifier(s, 104, "PurchaseOrder", "CLASS", packageId);
        String orderItem = classifier(s, 105, "OrderItem", "CLASS", packageId);
        String customer = classifier(s, 106, "Customer", "CLASS", packageId);
        String address = classifier(s, 107, "Address", "CLASS", packageId);
        association(s, 110, "SupplierProducts", packageId,
                supplier, "supplier", 1, 1, "SHARED", false,
                product, "products", 0, null, "NONE", true);
        association(s, 120, "PurchaseOrderItems", packageId,
                purchaseOrder, "purchaseOrder", 1, 1, "COMPOSITE", false,
                orderItem, "items", 1, null, "NONE", true);
        association(s, 130, "NormalLink", packageId,
                supplier, "normalSupplier", 1, 1, "NONE", false,
                purchaseOrder, "normalOrder", 1, 1, "NONE", false);
        association(s, 140, "CustomerAddresses", packageId,
                customer, "customer", 1, 1, "COMPOSITE", true,
                address, "addresses", 0, null, "NONE", false);
        return s;
    }

    private String classifier(ObjectNode s, int number, String name, String kind, String packageId) {
        String id = id(number);
        element(s, id, "CLASSIFIER", name);
        row(s, "classifiers").put("element_id", id).put("package_id", packageId).put("kind", kind)
                .put("visibility", "PUBLIC").put("is_abstract", false).put("is_active", false);
        return id;
    }

    private String property(ObjectNode s, int number, String name, String owner, String visibility,
            String typeKind, String primitive, String classifier, int lower, Integer upper) {
        String id = id(number);
        element(s, id, "PROPERTY", name);
        ObjectNode row = row(s, "properties").put("element_id", id).put("owner_classifier_id", owner)
                .put("visibility", visibility).put("type_kind", typeKind).put("lower_bound", lower)
                .put("is_static", false).put("is_read_only", false).put("is_derived", false);
        nullable(row, "primitive_type", primitive);
        nullable(row, "type_classifier_id", classifier);
        nullable(row, "upper_bound", upper);
        return id;
    }

    private void parameter(ObjectNode s, int number, String name, String operation, String direction,
            String primitive, int position) {
        String id = id(number);
        element(s, id, "PARAMETER", name);
        row(s, "parameters").put("element_id", id).put("operation_id", operation).put("direction", direction)
                .put("type_kind", "PRIMITIVE").put("primitive_type", primitive).putNull("type_classifier_id")
                .put("lower_bound", 1).put("upper_bound", 1).put("position", position);
    }

    private void association(ObjectNode s, int number, String name, String packageId,
            String firstClassifier, String firstRole, int firstLower, Integer firstUpper,
            String firstAggregation, boolean firstNavigable, String secondClassifier, String secondRole,
            int secondLower, Integer secondUpper, String secondAggregation, boolean secondNavigable) {
        String association = id(number);
        element(s, association, "ASSOCIATION", name);
        row(s, "associations").put("element_id", association).put("package_id", packageId);
        associationEnd(s, number + 1, association, firstClassifier, firstRole, firstLower, firstUpper,
                firstAggregation, firstNavigable, 1);
        associationEnd(s, number + 2, association, secondClassifier, secondRole, secondLower, secondUpper,
                secondAggregation, secondNavigable, 2);
    }

    private void associationEnd(ObjectNode s, int number, String association, String classifier, String role,
            int lower, Integer upper, String aggregation, boolean navigable, int order) {
        String property = id(number);
        element(s, property, "PROPERTY", role);
        ObjectNode p = row(s, "properties").put("element_id", property).putNull("owner_classifier_id")
                .put("visibility", "PUBLIC").put("type_kind", "CLASSIFIER").putNull("primitive_type")
                .put("type_classifier_id", classifier).put("lower_bound", lower).put("is_static", false)
                .put("is_read_only", false).put("is_derived", false);
        nullable(p, "upper_bound", upper);
        row(s, "associationEnds").put("property_id", property).put("association_id", association)
                .put("navigable", navigable).put("aggregation", aggregation).put("ownership", "ASSOCIATION")
                .put("member_order", order);
    }

    private ObjectNode element(ObjectNode s, String id, String type, String name) {
        ObjectNode result = row(s, "elements").put("id", id).put("element_type", type);
        nullable(result, "name", name);
        result.putNull("documentation").putNull("external_xmi_id");
        return result;
    }

    private ObjectNode row(ObjectNode s, String table) { return ((ArrayNode) s.path(table)).addObject(); }
    private void assertPrimitiveHref(javax.xml.xpath.XPath xpath, org.w3c.dom.Document document,
            String elementName, String primitive) throws Exception {
        assertEquals(primitive, primitiveName(xpath, document, "//*[@name='" + elementName + "']"));
    }
    private void assertAssociationEnd(javax.xml.xpath.XPath xpath, org.w3c.dom.Document document,
            String associationName, String classifierName, String roleName, String aggregation,
            String lower, String upper, String navigable) throws Exception {
        String classifierId = xpath.evaluate("//*[@name='" + classifierName + "']/@*[local-name()='id']", document);
        String association = "//*[@*[local-name()='type']='uml:Association'][@name='" + associationName + "']";
        String end = association + "/ownedEnd[type/@*[local-name()='idref']='" + classifierId + "']";
        assertEquals(1d, (Double) xpath.evaluate("count(" + end + ")", document, XPathConstants.NUMBER));
        assertEquals(roleName, xpath.evaluate(end + "/@name", document));
        assertEquals(aggregation, xpath.evaluate(end + "/@aggregation", document));
        assertEquals(lower, xpath.evaluate(end + "/lowerValue/@value", document));
        assertEquals(upper, xpath.evaluate(end + "/upperValue/@value", document));
        assertEquals(navigable, xpath.evaluate(end + "/@*[local-name()='navigable']", document));
        String endId = xpath.evaluate(end + "/@*[local-name()='id']", document);
        assertEquals(1d, (Double) xpath.evaluate(
                "count(" + association + "/memberEnd[@*[local-name()='idref']='" + endId + "'])",
                document, XPathConstants.NUMBER));
    }
    private void assertStandardNavigability(javax.xml.xpath.XPath xpath, org.w3c.dom.Document document,
            String associationName, String classifierName, boolean expected) throws Exception {
        String classifierId = xpath.evaluate("//*[@name='" + classifierName + "']/@*[local-name()='id']", document);
        String association = "//*[@*[local-name()='type']='uml:Association'][@name='" + associationName + "']";
        String endId = xpath.evaluate(association
                + "/ownedEnd[type/@*[local-name()='idref']='" + classifierId + "']/@*[local-name()='id']", document);
        String navigableIds = xpath.evaluate(association + "/@navigableOwnedEnd", document);
        boolean actual = java.util.Arrays.asList(navigableIds.trim().split("\\s+")).contains(endId);
        assertEquals(expected, actual);
    }
    private String primitiveName(javax.xml.xpath.XPath xpath, org.w3c.dom.Document document,
            String elementExpression) throws Exception {
        String href = xpath.evaluate(elementExpression + "/type/@href", document);
        assertTrue(href.startsWith(UmlXmiWriter.UML_PRIMITIVE_TYPES + "#"));
        return href.substring(href.indexOf('#') + 1);
    }
    private void removeById(ArrayNode rows, String field, String value) {
        for (int index = rows.size() - 1; index >= 0; index--) {
            if (value.equals(rows.get(index).path(field).asText())) rows.remove(index);
        }
    }
    private String id(int value) { return new UUID(0, value).toString(); }
    private Path fixture(String relative) {
        Path fromBackend = Path.of("..").resolve(relative);
        return Files.exists(fromBackend) ? fromBackend : Path.of(relative);
    }
    private void nullable(ObjectNode row, String key, String value) { if (value == null) row.putNull(key); else row.put(key, value); }
    private void nullable(ObjectNode row, String key, Integer value) { if (value == null) row.putNull(key); else row.put(key, value); }

    private org.w3c.dom.Document parse(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }
}
