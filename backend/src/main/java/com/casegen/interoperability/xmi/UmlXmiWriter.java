package com.casegen.interoperability.xmi;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import tools.jackson.databind.JsonNode;

/** Deterministic UML 2.5.1/XMI projection of the editable semantic model. */
@Component
public class UmlXmiWriter {
    static final String XMI_NS = "http://www.omg.org/spec/XMI/20131001";
    static final String UML_NS = "http://www.omg.org/spec/UML/20161101";
    static final String CASEGEN_NS = "https://casegen.dev/xmi/1.0";
    static final String UML_PRIMITIVE_TYPES = UML_NS + "/PrimitiveTypes.xmi";
    private static final Pattern SAFE_XMI_ID = Pattern.compile("[A-Za-z_][A-Za-z0-9_.-]*");
    private static final List<String> PRIMITIVES = List.of(
            "STRING", "INTEGER", "BOOLEAN", "REAL", "UNLIMITED_NATURAL");

    public String write(JsonNode snapshot) {
        Context context = new Context(snapshot);
        context.validate();
        try {
            StringWriter output = new StringWriter();
            XMLStreamWriter xml = XMLOutputFactory.newFactory().createXMLStreamWriter(output);
            writeDocument(xml, context);
            xml.close();
            String result = output.toString();
            validateGeneratedXml(result);
            return result;
        } catch (XMLStreamException exception) {
            throw invalid("Could not serialize UML/XMI", exception);
        }
    }

    private void writeDocument(XMLStreamWriter xml, Context c) throws XMLStreamException {
        JsonNode model = c.snapshot.path("model");
        xml.writeStartDocument("UTF-8", "1.0");
        xml.writeStartElement("xmi", "XMI", XMI_NS);
        xml.writeNamespace("xmi", XMI_NS);
        xml.writeNamespace("uml", UML_NS);
        xml.writeNamespace("casegen", CASEGEN_NS);
        xml.writeAttribute("xmi", XMI_NS, "version", "2.5.1");
        xml.writeStartElement("uml", "Model", UML_NS);
        xmiAttribute(xml, "type", "uml:Model");
        xmiAttribute(xml, "id", c.modelXmiId);
        attribute(xml, "name", text(model, "name"));
        extensionAttributes(xml, text(model, "id"), null, text(model, "documentation"));

        for (JsonNode packageRow : c.children(c.packages, "parent_package_id", null)) {
            writePackage(xml, c, packageRow);
        }
        writeContainerMembers(xml, c, null);

        xml.writeEndElement();
        xml.writeEndElement();
        xml.writeEndDocument();
    }

    private void writePackage(XMLStreamWriter xml, Context c, JsonNode row) throws XMLStreamException {
        String id = text(row, "element_id");
        JsonNode element = c.element(id);
        xml.writeStartElement("packagedElement");
        xmiAttribute(xml, "type", "uml:Package");
        xmiAttribute(xml, "id", c.xmiId(id));
        attribute(xml, "name", text(element, "name"));
        extensionAttributes(xml, id, text(element, "external_xmi_id"), text(element, "documentation"));
        for (JsonNode child : c.children(c.packages, "parent_package_id", id)) {
            writePackage(xml, c, child);
        }
        writeContainerMembers(xml, c, id);
        xml.writeEndElement();
    }

    private void writeContainerMembers(XMLStreamWriter xml, Context c, String packageId)
            throws XMLStreamException {
        for (JsonNode classifier : c.children(c.classifiers, "package_id", packageId)) {
            writeClassifier(xml, c, classifier);
        }
        for (JsonNode association : c.children(c.associations, "package_id", packageId)) {
            writeAssociation(xml, c, association);
        }
        for (JsonNode realization : c.realizationsForPackage(packageId)) {
            writeRealization(xml, c, realization);
        }
    }

    private void writeClassifier(XMLStreamWriter xml, Context c, JsonNode row) throws XMLStreamException {
        String id = text(row, "element_id");
        JsonNode element = c.element(id);
        String kind = text(row, "kind");
        xml.writeStartElement("packagedElement");
        xmiAttribute(xml, "type", "INTERFACE".equals(kind) ? "uml:Interface" : "uml:Class");
        xmiAttribute(xml, "id", c.xmiId(id));
        attribute(xml, "name", text(element, "name"));
        attribute(xml, "visibility", lower(text(row, "visibility")));
        attribute(xml, "isAbstract", bool(row, "is_abstract"));
        if ("CLASS".equals(kind)) attribute(xml, "isActive", bool(row, "is_active"));
        extensionAttributes(xml, id, text(element, "external_xmi_id"), text(element, "documentation"));

        for (JsonNode property : c.propertiesForClassifier(id)) writeProperty(xml, c, property);
        for (JsonNode operation : c.children(c.operations, "owner_classifier_id", id)) {
            writeOperation(xml, c, operation);
        }
        for (JsonNode generalization : c.children(c.generalizations, "specific_classifier_id", id)) {
            xml.writeStartElement("generalization");
            xmiAttribute(xml, "type", "uml:Generalization");
            xmiAttribute(xml, "id", c.xmiId(text(generalization, "element_id")));
            attribute(xml, "general", c.xmiId(text(generalization, "general_classifier_id")));
            attribute(xml, "isSubstitutable", bool(generalization, "is_substitutable"));
            extensionAttributes(xml, text(generalization, "element_id"),
                    c.externalId(text(generalization, "element_id")),
                    c.documentation(text(generalization, "element_id")));
            xml.writeEndElement();
        }
        xml.writeEndElement();
    }

    private void writeProperty(XMLStreamWriter xml, Context c, JsonNode row) throws XMLStreamException {
        String id = text(row, "element_id");
        JsonNode element = c.element(id);
        JsonNode end = c.endsByProperty.get(id);
        xml.writeStartElement("ownedAttribute");
        xmiAttribute(xml, "type", "uml:Property");
        xmiAttribute(xml, "id", c.xmiId(id));
        attribute(xml, "name", text(element, "name"));
        attribute(xml, "visibility", lower(text(row, "visibility")));
        attribute(xml, "isStatic", bool(row, "is_static"));
        attribute(xml, "isReadOnly", bool(row, "is_read_only"));
        attribute(xml, "isDerived", bool(row, "is_derived"));
        if (end != null) {
            attribute(xml, "association", c.xmiId(text(end, "association_id")));
            attribute(xml, "aggregation", c.eaAggregationForProperty(id));
            casegenAttribute(xml, "navigable", bool(end, "navigable"));
            casegenAttribute(xml, "ownership", text(end, "ownership"));
        }
        extensionAttributes(xml, id, text(element, "external_xmi_id"), text(element, "documentation"));
        writeTypeAndMultiplicity(xml, c, row);
        xml.writeEndElement();
    }

    private void writeOperation(XMLStreamWriter xml, Context c, JsonNode row) throws XMLStreamException {
        String id = text(row, "element_id");
        JsonNode element = c.element(id);
        xml.writeStartElement("ownedOperation");
        xmiAttribute(xml, "type", "uml:Operation");
        xmiAttribute(xml, "id", c.xmiId(id));
        attribute(xml, "name", text(element, "name"));
        attribute(xml, "visibility", lower(text(row, "visibility")));
        attribute(xml, "isAbstract", bool(row, "is_abstract"));
        attribute(xml, "isStatic", bool(row, "is_static"));
        attribute(xml, "isQuery", bool(row, "is_query"));
        extensionAttributes(xml, id, text(element, "external_xmi_id"), text(element, "documentation"));
        List<JsonNode> parameters = c.children(c.parameters, "operation_id", id);
        parameters.sort(Comparator.comparingInt((JsonNode node) -> node.path("position").asInt())
                .thenComparing(node -> text(node, "element_id")));
        for (JsonNode parameter : parameters) writeParameter(xml, c, parameter);
        xml.writeEndElement();
    }

    private void writeParameter(XMLStreamWriter xml, Context c, JsonNode row) throws XMLStreamException {
        String id = text(row, "element_id");
        JsonNode element = c.element(id);
        xml.writeStartElement("ownedParameter");
        xmiAttribute(xml, "type", "uml:Parameter");
        xmiAttribute(xml, "id", c.xmiId(id));
        attribute(xml, "name", text(element, "name"));
        attribute(xml, "direction", parameterDirection(text(row, "direction")));
        casegenAttribute(xml, "position", Integer.toString(row.path("position").asInt()));
        extensionAttributes(xml, id, text(element, "external_xmi_id"), text(element, "documentation"));
        writeTypeAndMultiplicity(xml, c, row);
        xml.writeEndElement();
    }

    private void writeAssociation(XMLStreamWriter xml, Context c, JsonNode row) throws XMLStreamException {
        String id = text(row, "element_id");
        JsonNode element = c.element(id);
        List<JsonNode> ends = c.children(c.associationEnds, "association_id", id);
        ends.sort(Comparator.comparingInt(node -> node.path("member_order").asInt()));
        xml.writeStartElement("packagedElement");
        xmiAttribute(xml, "type", "uml:Association");
        xmiAttribute(xml, "id", c.xmiId(id));
        attribute(xml, "name", text(element, "name"));
        String navigable = joinNavigableOwnedEndIds(c, ends);
        if (!navigable.isBlank()) attribute(xml, "navigableOwnedEnd", navigable);
        extensionAttributes(xml, id, text(element, "external_xmi_id"), text(element, "documentation"));
        for (JsonNode end : ends) {
            xml.writeStartElement("memberEnd");
            xmiAttribute(xml, "idref", c.xmiId(text(end, "property_id")));
            xml.writeEndElement();
            if (!"ASSOCIATION".equals(text(end, "ownership"))) continue;
            JsonNode property = c.properties.get(text(end, "property_id"));
            JsonNode propertyElement = c.element(text(end, "property_id"));
            xml.writeStartElement("ownedEnd");
            xmiAttribute(xml, "type", "uml:Property");
            xmiAttribute(xml, "id", c.xmiId(text(end, "property_id")));
            attribute(xml, "name", text(propertyElement, "name"));
            attribute(xml, "visibility", lower(text(property, "visibility")));
            attribute(xml, "association", c.xmiId(id));
            attribute(xml, "aggregation", c.eaAggregationForProperty(text(end, "property_id")));
            casegenAttribute(xml, "navigable", bool(end, "navigable"));
            casegenAttribute(xml, "ownership", text(end, "ownership"));
            extensionAttributes(xml, text(end, "property_id"), text(propertyElement, "external_xmi_id"),
                    text(propertyElement, "documentation"));
            writeTypeAndMultiplicity(xml, c, property);
            xml.writeEndElement();
        }
        xml.writeEndElement();
    }

    private String joinNavigableOwnedEndIds(Context c, List<JsonNode> ends) {
        List<String> result = new ArrayList<>();
        for (JsonNode end : ends) {
            boolean associationOwned = "ASSOCIATION".equals(text(end, "ownership"));
            if (!end.path("navigable").asBoolean() || !associationOwned) continue;
            result.add(c.xmiId(text(end, "property_id")));
        }
        return String.join(" ", result);
    }

    private void writeTypeAndMultiplicity(XMLStreamWriter xml, Context c, JsonNode row)
            throws XMLStreamException {
        xml.writeStartElement("type");
        if ("PRIMITIVE".equals(text(row, "type_kind"))) {
            xmiAttribute(xml, "type", "uml:PrimitiveType");
            attribute(xml, "href", primitiveHref(text(row, "primitive_type")));
        } else {
            xmiAttribute(xml, "idref", c.classifierTypeId(row));
        }
        xml.writeEndElement();
        xml.writeStartElement("lowerValue");
        xmiAttribute(xml, "type", "uml:LiteralInteger");
        attribute(xml, "value", Integer.toString(row.path("lower_bound").asInt()));
        xml.writeEndElement();
        JsonNode upper = row.path("upper_bound");
        xml.writeStartElement("upperValue");
        xmiAttribute(xml, "type", "uml:LiteralUnlimitedNatural");
        attribute(xml, "value", upper.isNull() || upper.isMissingNode() ? "*" : upper.asText());
        xml.writeEndElement();
    }

    private void writeRealization(XMLStreamWriter xml, Context c, JsonNode realization)
            throws XMLStreamException {
        String id = text(realization, "element_id");
        xml.writeStartElement("packagedElement");
        xmiAttribute(xml, "type", "uml:Realization");
        xmiAttribute(xml, "id", c.xmiId(id));
        attribute(xml, "client", c.xmiId(text(realization, "client_classifier_id")));
        attribute(xml, "supplier", c.xmiId(text(realization, "supplier_classifier_id")));
        extensionAttributes(xml, id, c.externalId(id), c.documentation(id));
        xml.writeEndElement();
    }

    private void extensionAttributes(XMLStreamWriter xml, String internalId, String externalId,
            String documentation) throws XMLStreamException {
        casegenAttribute(xml, "internalId", internalId);
        if (externalId != null) casegenAttribute(xml, "externalXmiId", externalId);
        if (documentation != null) casegenAttribute(xml, "documentation", documentation);
    }

    private void xmiAttribute(XMLStreamWriter xml, String name, String value) throws XMLStreamException {
        xml.writeAttribute("xmi", XMI_NS, name, value);
    }

    private void casegenAttribute(XMLStreamWriter xml, String name, String value) throws XMLStreamException {
        if (value != null) xml.writeAttribute("casegen", CASEGEN_NS, name, value);
    }

    private void attribute(XMLStreamWriter xml, String name, String value) throws XMLStreamException {
        if (value != null && !value.isBlank()) xml.writeAttribute(name, value);
    }

    private void validateGeneratedXml(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
            Set<String> ids = new HashSet<>();
            var elements = document.getElementsByTagName("*");
            for (int index = 0; index < elements.getLength(); index++) {
                String id = elements.item(index).getAttributes().getNamedItemNS(XMI_NS, "id") == null ? null
                        : elements.item(index).getAttributes().getNamedItemNS(XMI_NS, "id").getNodeValue();
                if (id != null && !ids.add(id)) throw invalid("Duplicate xmi:id: " + id, null);
            }
            Set<String> referenceAttributes = Set.of("association", "client", "contract", "general",
                    "memberEnd", "navigableOwnedEnd", "supplier", "type");
            for (int index = 0; index < elements.getLength(); index++) {
                NamedNodeMap attributes = elements.item(index).getAttributes();
                for (int attributeIndex = 0; attributeIndex < attributes.getLength(); attributeIndex++) {
                    Node attribute = attributes.item(attributeIndex);
                    if (attribute.getNamespaceURI() != null || !referenceAttributes.contains(attribute.getNodeName())) continue;
                    for (String reference : attribute.getNodeValue().split("\\s+")) {
                        if (!reference.isBlank() && !ids.contains(reference)) {
                            throw invalid("Unresolved XMI reference: " + reference, null);
                        }
                    }
                }
                Node idref = attributes.getNamedItemNS(XMI_NS, "idref");
                if (idref != null && !ids.contains(idref.getNodeValue())) {
                    throw invalid("Unresolved XMI reference: " + idref.getNodeValue(), null);
                }
                Node href = attributes.getNamedItem("href");
                if (href != null && !isSupportedPrimitiveHref(href.getNodeValue())) {
                    throw invalid("Unsupported external XMI reference: " + href.getNodeValue(), null);
                }
            }
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalid("Generated XMI is not well-formed", exception);
        }
    }

    private static String text(JsonNode row, String field) {
        if (row == null) return null;
        JsonNode value = row.path(field);
        return value.isMissingNode() || value.isNull() || value.asText().isBlank() ? null : value.asText();
    }

    private static String bool(JsonNode row, String field) {
        return Boolean.toString(row.path(field).asBoolean());
    }

    private static String lower(String value) {
        return value == null ? null : value.toLowerCase(java.util.Locale.ROOT);
    }

    private static String parameterDirection(String value) {
        return "INOUT".equals(value) ? "inout" : lower(value);
    }

    private static String primitiveHref(String primitive) {
        return UML_PRIMITIVE_TYPES + "#" + primitiveName(primitive);
    }

    private static boolean isSupportedPrimitiveHref(String href) {
        for (String primitive : PRIMITIVES) {
            if (primitiveHref(primitive).equals(href)) return true;
        }
        return false;
    }

    private static String primitiveName(String primitive) {
        return switch (primitive) {
            case "STRING" -> "String";
            case "INTEGER" -> "Integer";
            case "BOOLEAN" -> "Boolean";
            case "REAL" -> "Real";
            case "UNLIMITED_NATURAL" -> "UnlimitedNatural";
            default -> throw invalid("Unsupported primitive type: " + primitive, null);
        };
    }

    private static ResponseStatusException invalid(String message, Throwable cause) {
        return new ResponseStatusException(HttpStatus.CONFLICT, "XMI_EXPORT_INVALID: " + message, cause);
    }

    private static final class Context {
        private final JsonNode snapshot;
        private final Map<String, JsonNode> elements;
        private final Map<String, JsonNode> packages;
        private final Map<String, JsonNode> classifiers;
        private final Map<String, JsonNode> properties;
        private final Map<String, JsonNode> operations;
        private final Map<String, JsonNode> parameters;
        private final Map<String, JsonNode> associations;
        private final Map<String, JsonNode> generalizations;
        private final Map<String, JsonNode> realizations;
        private final List<JsonNode> associationEnds;
        private final Map<String, JsonNode> endsByProperty;
        private final Map<String, String> xmiIds = new HashMap<>();
        private final String modelXmiId;

        private Context(JsonNode snapshot) {
            this.snapshot = snapshot;
            elements = index(snapshot.path("elements"), "id");
            packages = index(snapshot.path("packages"), "element_id");
            classifiers = index(snapshot.path("classifiers"), "element_id");
            properties = index(snapshot.path("properties"), "element_id");
            operations = index(snapshot.path("operations"), "element_id");
            parameters = index(snapshot.path("parameters"), "element_id");
            associations = index(snapshot.path("associations"), "element_id");
            generalizations = index(snapshot.path("generalizations"), "element_id");
            realizations = index(snapshot.path("realizations"), "element_id");
            associationEnds = list(snapshot.path("associationEnds"));
            endsByProperty = index(snapshot.path("associationEnds"), "property_id");
            Set<String> used = new LinkedHashSet<>();
            String modelId = required(snapshot.path("model"), "id");
            modelXmiId = uniqueId("casegen_model_" + modelId, used);
            for (Map.Entry<String, JsonNode> entry : elements.entrySet()) {
                String external = text(entry.getValue(), "external_xmi_id");
                String candidate = external != null && SAFE_XMI_ID.matcher(external).matches()
                        ? external : "casegen_" + entry.getKey();
                xmiIds.put(entry.getKey(), uniqueId(candidate, used));
            }
        }

        private void validate() {
            if (snapshot.path("model").isMissingNode() || snapshot.path("model").isNull()) fail("Model is missing");
            validateSpecializations(packages, "PACKAGE");
            validateSpecializations(classifiers, "CLASSIFIER");
            validateSpecializations(properties, "PROPERTY");
            validateSpecializations(operations, "OPERATION");
            validateSpecializations(parameters, "PARAMETER");
            validateSpecializations(associations, "ASSOCIATION");
            validateSpecializations(generalizations, "GENERALIZATION");
            validateSpecializations(realizations, "REALIZATION");
            for (JsonNode row : packages.values()) {
                optionalReference(row, "parent_package_id", packages);
                ensureNoPackageCycle(text(row, "element_id"));
            }
            for (JsonNode row : classifiers.values()) optionalReference(row, "package_id", packages);
            for (JsonNode row : properties.values()) {
                boolean associationEnd = endsByProperty.containsKey(text(row, "element_id"));
                reference(row, "owner_classifier_id", classifiers, !associationEnd);
                validateType(row);
                validateMultiplicity(row);
            }
            for (JsonNode row : operations.values()) reference(row, "owner_classifier_id", classifiers, true);
            Map<String, Integer> returns = new HashMap<>();
            for (JsonNode row : parameters.values()) {
                reference(row, "operation_id", operations, true);
                validateType(row);
                validateMultiplicity(row);
                if ("RETURN".equals(text(row, "direction"))) {
                    String operation = text(row, "operation_id");
                    if (returns.merge(operation, 1, Integer::sum) > 1) fail("Operation has more than one RETURN parameter");
                }
            }
            for (JsonNode row : associations.values()) {
                optionalReference(row, "package_id", packages);
                List<JsonNode> ends = children(associationEnds, "association_id", text(row, "element_id"));
                if (ends.size() != 2) fail("Association must have exactly two ends: " + text(row, "element_id"));
                Set<Integer> orders = new HashSet<>();
                for (JsonNode end : ends) {
                    reference(end, "property_id", properties, true);
                    if (!orders.add(end.path("member_order").asInt())) fail("Association end order is duplicated");
                    JsonNode property = properties.get(text(end, "property_id"));
                    if (!"CLASSIFIER".equals(text(property, "type_kind"))) fail("Association end must use classifier type");
                    String ownership = text(end, "ownership");
                    if ("CLASSIFIER".equals(ownership)) reference(property, "owner_classifier_id", classifiers, true);
                    if ("ASSOCIATION".equals(ownership) && text(property, "owner_classifier_id") != null) {
                        fail("Association-owned end cannot have a classifier owner");
                    }
                }
            }
            validateGeneralizations();
            for (JsonNode row : realizations.values()) {
                reference(row, "client_classifier_id", classifiers, true);
                reference(row, "supplier_classifier_id", classifiers, true);
                String client = text(row, "client_classifier_id");
                String supplier = text(row, "supplier_classifier_id");
                if (client.equals(supplier) || !"CLASS".equals(text(classifiers.get(client), "kind"))
                        || !"INTERFACE".equals(text(classifiers.get(supplier), "kind"))) {
                    fail("Realization must be CLASS -> INTERFACE");
                }
            }
        }

        private void validateSpecializations(Map<String, JsonNode> rows, String type) {
            for (String id : rows.keySet()) {
                JsonNode element = elements.get(id);
                if (element == null || !type.equals(text(element, "element_type"))) {
                    fail(type + " specialization has no matching UmlElement: " + id);
                }
            }
        }

        private void validateType(JsonNode row) {
            String kind = text(row, "type_kind");
            String primitive = text(row, "primitive_type");
            String classifier = text(row, "type_classifier_id");
            if ("PRIMITIVE".equals(kind) && primitive != null && classifier == null && PRIMITIVES.contains(primitive)) return;
            if ("CLASSIFIER".equals(kind) && primitive == null && classifier != null && classifiers.containsKey(classifier)) return;
            fail("Invalid or unresolved type for element " + text(row, "element_id"));
        }

        private void validateMultiplicity(JsonNode row) {
            int lower = row.path("lower_bound").asInt(-1);
            JsonNode upperNode = row.path("upper_bound");
            Integer upper = upperNode.isNull() || upperNode.isMissingNode() ? null : upperNode.asInt();
            if (lower < 0 || upper != null && upper < lower) fail("Invalid multiplicity for element " + text(row, "element_id"));
        }

        private void validateGeneralizations() {
            Map<String, Set<String>> graph = new HashMap<>();
            for (JsonNode row : generalizations.values()) {
                reference(row, "specific_classifier_id", classifiers, true);
                reference(row, "general_classifier_id", classifiers, true);
                String specific = text(row, "specific_classifier_id");
                String general = text(row, "general_classifier_id");
                if (specific.equals(general) || !text(classifiers.get(specific), "kind")
                        .equals(text(classifiers.get(general), "kind"))) fail("Invalid generalization");
                graph.computeIfAbsent(specific, ignored -> new HashSet<>()).add(general);
            }
            for (String classifier : graph.keySet()) if (hasCycle(classifier, graph, new HashSet<>(), new HashSet<>())) {
                fail("Generalization cycle detected");
            }
        }

        private boolean hasCycle(String node, Map<String, Set<String>> graph, Set<String> visited, Set<String> path) {
            if (!path.add(node)) return true;
            if (visited.add(node)) for (String next : graph.getOrDefault(node, Set.of())) {
                if (hasCycle(next, graph, visited, path)) return true;
            }
            path.remove(node);
            return false;
        }

        private void ensureNoPackageCycle(String start) {
            Set<String> visited = new HashSet<>();
            String current = start;
            while (current != null) {
                if (!visited.add(current)) fail("Package containment cycle detected");
                JsonNode row = packages.get(current);
                current = row == null ? null : text(row, "parent_package_id");
            }
        }

        private String classifierTypeId(JsonNode row) {
            return xmiId(text(row, "type_classifier_id"));
        }

        /**
         * Enterprise Architect serializes shared/composite on the part-typed
         * association property, while CASEGen stores it on the visual whole end.
         * Only this XMI boundary projection is inverted; all other end data stays
         * attached to its original property. Applying the same opposite-end rule
         * on a future import restores CASEGen's representation.
         */
        private String eaAggregationForProperty(String propertyId) {
            JsonNode current = endsByProperty.get(propertyId);
            if (current == null) return null;
            String associationId = text(current, "association_id");
            for (JsonNode candidate : associationEnds) {
                if (associationId.equals(text(candidate, "association_id"))
                        && !propertyId.equals(text(candidate, "property_id"))) {
                    return lower(text(candidate, "aggregation"));
                }
            }
            fail("Association end has no opposite end: " + propertyId);
            return null;
        }

        private JsonNode element(String id) {
            JsonNode result = elements.get(id);
            if (result == null) fail("UmlElement not found: " + id);
            return result;
        }

        private String xmiId(String internalId) {
            String result = xmiIds.get(internalId);
            if (result == null) fail("XMI id not found for UML element: " + internalId);
            return result;
        }

        private String externalId(String id) { return text(element(id), "external_xmi_id"); }
        private String documentation(String id) { return text(element(id), "documentation"); }

        private List<JsonNode> propertiesForClassifier(String classifierId) {
            List<JsonNode> result = new ArrayList<>();
            for (JsonNode property : properties.values()) {
                if (!classifierId.equals(text(property, "owner_classifier_id"))) continue;
                JsonNode end = endsByProperty.get(text(property, "element_id"));
                if (end == null || "CLASSIFIER".equals(text(end, "ownership"))) result.add(property);
            }
            result.sort(Comparator.comparing(node -> text(node, "element_id")));
            return result;
        }

        private List<JsonNode> realizationsForPackage(String packageId) {
            List<JsonNode> result = new ArrayList<>();
            for (JsonNode realization : realizations.values()) {
                JsonNode client = classifiers.get(text(realization, "client_classifier_id"));
                if (client != null && java.util.Objects.equals(text(client, "package_id"), packageId)) {
                    result.add(realization);
                }
            }
            result.sort(Comparator.comparing(node -> text(node, "element_id")));
            return result;
        }

        private List<JsonNode> children(Map<String, JsonNode> rows, String field, String parent) {
            return children(new ArrayList<>(rows.values()), field, parent);
        }

        private List<JsonNode> children(List<JsonNode> rows, String field, String parent) {
            List<JsonNode> result = new ArrayList<>();
            for (JsonNode row : rows) if (java.util.Objects.equals(text(row, field), parent)) result.add(row);
            result.sort(Comparator.comparing(node -> text(node, keyFor(rows, node))));
            return result;
        }

        private String keyFor(List<JsonNode> rows, JsonNode node) {
            return node.has("element_id") ? "element_id" : node.has("property_id") ? "property_id" : "id";
        }

        private void reference(JsonNode row, String field, Map<String, JsonNode> targets, boolean required) {
            String value = text(row, field);
            if (value == null) {
                if (required) fail("Missing reference " + field);
            } else if (!targets.containsKey(value)) fail("Unresolved reference " + field + ": " + value);
        }

        private void optionalReference(JsonNode row, String field, Map<String, JsonNode> targets) {
            reference(row, field, targets, false);
        }

        private static Map<String, JsonNode> index(JsonNode rows, String key) {
            Map<String, JsonNode> result = new TreeMap<>();
            if (rows.isArray()) for (JsonNode row : rows) {
                String id = required(row, key);
                if (result.put(id, row) != null) fail("Duplicate semantic identifier: " + id);
            }
            return result;
        }

        private static List<JsonNode> list(JsonNode rows) {
            List<JsonNode> result = new ArrayList<>();
            if (rows.isArray()) rows.forEach(result::add);
            return result;
        }

        private static String required(JsonNode row, String key) {
            String value = text(row, key);
            if (value == null) fail("Required field is missing: " + key);
            return value;
        }

        private static String uniqueId(String candidate, Set<String> used) {
            String value = candidate;
            int suffix = 2;
            while (!used.add(value)) value = candidate + "_" + suffix++;
            return value;
        }

        private static void fail(String message) {
            throw invalid(message, null);
        }
    }
}
