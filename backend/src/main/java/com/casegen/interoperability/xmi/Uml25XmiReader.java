package com.casegen.interoperability.xmi;

import static com.casegen.interoperability.xmi.XmiDom.*;
import static com.casegen.interoperability.xmi.XmiImportData.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/** Common UML 2.5.1 reader used by CASEGen and modern EA XMI adapters. */
final class Uml25XmiReader {
    private final List<PackageData> packages = new ArrayList<>();
    private final List<ClassifierData> classifiers = new ArrayList<>();
    private final List<PropertyData> properties = new ArrayList<>();
    private final List<OperationData> operations = new ArrayList<>();
    private final List<ParameterData> parameters = new ArrayList<>();
    private final List<AssociationData> associations = new ArrayList<>();
    private final List<GeneralizationData> generalizations = new ArrayList<>();
    private final List<RealizationData> realizations = new ArrayList<>();
    private final List<XmiImportWarning> warnings = new ArrayList<>();
    private final Map<String, Element> elements = new LinkedHashMap<>();
    private final Map<String, String> propertyOwners = new HashMap<>();
    private final Set<String> classifierIds = new HashSet<>();

    XmiImportData read(Document document) {
        Element model = descendants(document.getDocumentElement(), "Model").stream().findFirst()
                .orElseThrow(() -> invalid("XMI_MODEL_MISSING", "The XMI document has no UML Model"));
        indexElements(model);
        for (Element packaged : children(model, "packagedElement")) readPackaged(packaged, null);
        readRelationships(model);
        String name = value(attribute(model, "name"), "Imported model");
        if ("EA_Model".equals(name) && !packages.isEmpty()) name = packages.getFirst().name();
        return new XmiImportData(name, id(model), List.copyOf(packages), List.copyOf(classifiers),
                List.copyOf(properties), List.copyOf(operations), List.copyOf(parameters),
                List.copyOf(associations), List.copyOf(generalizations), List.copyOf(realizations),
                List.copyOf(warnings));
    }

    private void indexElements(Element root) {
        String rootId = id(root);
        if (rootId != null) elements.putIfAbsent(rootId, root);
        for (Element child : children(root)) indexElements(child);
    }

    private void readPackaged(Element element, String packageId) {
        String umlType = simpleType(type(element));
        switch (umlType) {
            case "Package" -> readPackage(element, packageId);
            case "Class", "Interface" -> readClassifier(element, packageId, umlType);
            case "Association", "Realization" -> { /* pass 3 */ }
            default -> warnings.add(new XmiImportWarning("UNSUPPORTED_XMI_ELEMENT",
                    "Unsupported UML element ignored: " + value(umlType, local(element)), id(element)));
        }
    }

    private void readPackage(Element element, String parentId) {
        String externalId = requiredId(element, "Package");
        packages.add(new PackageData(externalId, requiredName(element, "Package"), parentId));
        for (Element packaged : children(element, "packagedElement")) readPackaged(packaged, externalId);
    }

    private void readClassifier(Element element, String packageId, String umlType) {
        String externalId = requiredId(element, umlType);
        classifierIds.add(externalId);
        classifiers.add(new ClassifierData(externalId, requiredName(element, umlType), packageId,
                umlType.toUpperCase(Locale.ROOT), visibility(element, "PUBLIC"),
                bool(element, "isAbstract", "Interface".equals(umlType)), bool(element, "isActive", false)));
        for (Element attribute : children(element, "ownedAttribute")) {
            String propertyId = requiredId(attribute, "Property");
            propertyOwners.put(propertyId, externalId);
            if (attribute(element, "association") == null && attribute(attribute, "association") == null) {
                properties.add(property(attribute, externalId));
            }
        }
        for (Element operation : children(element, "ownedOperation")) readOperation(operation, externalId);
        for (Element generalization : children(element, "generalization")) {
            generalizations.add(new GeneralizationData(requiredId(generalization, "Generalization"),
                    externalId, requiredReference(generalization, "general"),
                    bool(generalization, "isSubstitutable", true)));
        }
    }

    private PropertyData property(Element element, String ownerId) {
        return new PropertyData(requiredId(element, "Property"), requiredName(element, "Property"), ownerId,
                visibility(element, "PRIVATE"), typedValue(element), lower(element), upper(element),
                bool(element, "isStatic", false), bool(element, "isReadOnly", false),
                bool(element, "isDerived", false));
    }

    private void readOperation(Element element, String ownerId) {
        String operationId = requiredId(element, "Operation");
        operations.add(new OperationData(operationId, requiredName(element, "Operation"), ownerId,
                visibility(element, "PUBLIC"), bool(element, "isAbstract", false),
                bool(element, "isStatic", false), bool(element, "isQuery", false)));
        int position = 0;
        for (Element parameter : children(element, "ownedParameter")) {
            String direction = value(attribute(parameter, "direction"), "in").toUpperCase(Locale.ROOT);
            if ("RETURN".equals(direction)) direction = "RETURN";
            parameters.add(new ParameterData(requiredId(parameter, "Parameter"),
                    "RETURN".equals(direction) ? optionalName(parameter) : requiredName(parameter, "Parameter"),
                    operationId, direction, typedValue(parameter), lower(parameter), upper(parameter), position++));
        }
    }

    private void readRelationships(Element model) {
        for (Element packaged : descendants(model, "packagedElement")) {
            String umlType = simpleType(type(packaged));
            if ("Association".equals(umlType)) readAssociation(packaged);
            else if ("Realization".equals(umlType)) realizations.add(new RealizationData(
                    requiredId(packaged, "Realization"), requiredReference(packaged, "client"),
                    requiredReference(packaged, "supplier")));
        }
    }

    private void readAssociation(Element association) {
        List<String> members = new ArrayList<>();
        String memberAttribute = attribute(association, "memberEnd");
        if (memberAttribute != null) members.addAll(List.of(memberAttribute.trim().split("\\s+")));
        for (Element member : children(association, "memberEnd")) members.add(requiredIdRef(member));
        if (members.isEmpty()) for (Element owned : children(association, "ownedEnd")) members.add(requiredId(owned, "AssociationEnd"));
        members = members.stream().distinct().toList();
        if (members.size() != 2) throw invalid("XMI_ASSOCIATION_END_COUNT",
                "Association " + id(association) + " must reference exactly two ends");
        Set<String> navigableIds = navigableIds(association);
        RawEnd first = rawEnd(members.get(0), association, navigableIds);
        RawEnd second = rawEnd(members.get(1), association, navigableIds);
        // EA stores aggregation on the part-typed Property. CASEGen stores it on
        // the opposite visual whole end. Multiplicity, roles and navigation stay put.
        AssociationEndData casegenFirst = first.toData(normalizeAggregation(second.xmiAggregation));
        AssociationEndData casegenSecond = second.toData(normalizeAggregation(first.xmiAggregation));
        associations.add(new AssociationData(requiredId(association, "Association"),
                optionalName(association), containingPackageId(association), casegenFirst, casegenSecond));
    }

    private RawEnd rawEnd(String propertyId, Element association, Set<String> navigableIds) {
        Element property = elements.get(propertyId);
        if (property == null) throw unresolved("association end", propertyId);
        String classifier = classifierReference(property);
        String owner = propertyOwners.get(propertyId);
        boolean standardNavigable = navigableIds.contains(propertyId) || owner != null;
        String metadata = attribute(property, "navigable");
        boolean navigable = standardNavigable || (metadata != null && Boolean.parseBoolean(metadata));
        return new RawEnd(propertyId, optionalName(property), classifier, visibility(property, "PUBLIC"),
                lower(property), upper(property), navigable, value(attribute(property, "aggregation"), "none"),
                owner == null ? "ASSOCIATION" : "CLASSIFIER");
    }

    private Set<String> navigableIds(Element association) {
        Set<String> result = new HashSet<>();
        String attribute = attribute(association, "navigableOwnedEnd");
        if (attribute != null) result.addAll(List.of(attribute.split("\\s+")));
        for (Element child : children(association, "navigableOwnedEnd")) result.add(requiredIdRef(child));
        return result;
    }

    private TypedValue typedValue(Element owner) {
        Element typeElement = child(owner, "type");
        if (typeElement == null) throw invalid("XMI_TYPE_MISSING", "Type is missing for " + id(owner));
        String href = attribute(typeElement, "href");
        if (href != null && href.contains("#")) return primitive(href.substring(href.lastIndexOf('#') + 1));
        String reference = idref(typeElement);
        if (reference != null) {
            if (!classifierIds.contains(reference) && !elements.containsKey(reference)) throw unresolved("type", reference);
            return new TypedValue("CLASSIFIER", null, reference);
        }
        throw invalid("XMI_TYPE_UNSUPPORTED", "Unsupported type for " + id(owner));
    }

    private TypedValue primitive(String name) {
        String primitive = switch (name.toLowerCase(Locale.ROOT)) {
            case "string" -> "STRING"; case "integer", "int" -> "INTEGER";
            case "boolean", "bool" -> "BOOLEAN"; case "real", "double", "float" -> "REAL";
            case "unlimitednatural", "unlimited_natural" -> "UNLIMITED_NATURAL";
            default -> throw invalid("XMI_PRIMITIVE_UNSUPPORTED", "Unsupported primitive type: " + name);
        };
        return new TypedValue("PRIMITIVE", primitive, null);
    }

    private String classifierReference(Element property) {
        TypedValue type = typedValue(property);
        if (!"CLASSIFIER".equals(type.typeKind())) throw invalid("XMI_ASSOCIATION_TYPE_INVALID",
                "Association end must reference a Classifier: " + id(property));
        return type.classifierExternalId();
    }

    private int lower(Element element) {
        Element value = child(element, "lowerValue");
        return value == null ? 1 : parseBound(attribute(value, "value"), 1, false);
    }

    private Integer upper(Element element) {
        Element value = child(element, "upperValue");
        if (value == null) return 1;
        String bound = attribute(value, "value");
        return "*".equals(bound) || "-1".equals(bound) ? null : parseBound(bound, 1, false);
    }

    private int parseBound(String value, int fallback, boolean unlimited) {
        if (value == null || value.isBlank()) return fallback;
        if (unlimited && "*".equals(value)) return -1;
        try { return Integer.parseInt(value); }
        catch (NumberFormatException exception) { throw invalid("XMI_MULTIPLICITY_INVALID", "Invalid bound: " + value); }
    }

    private String containingPackageId(Element element) {
        for (var node = element.getParentNode(); node instanceof Element parent; node = parent.getParentNode()) {
            if ("Package".equals(simpleType(type(parent)))) return id(parent);
        }
        return null;
    }

    private String visibility(Element element, String fallback) {
        String value = value(attribute(element, "visibility"), fallback).toUpperCase(Locale.ROOT);
        return "PACKAGE_PRIVATE".equals(value) ? "PACKAGE" : value;
    }

    private String normalizeAggregation(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "shared", "aggregate" -> "SHARED"; case "composite" -> "COMPOSITE"; default -> "NONE";
        };
    }

    private String requiredReference(Element element, String name) {
        String result = attribute(element, name);
        if (result == null) {
            Element child = child(element, name);
            if (child != null) result = idref(child);
        }
        if (result == null) throw invalid("XMI_REFERENCE_MISSING", name + " reference is missing on " + id(element));
        if (!elements.containsKey(result)) throw unresolved(name, result);
        return result;
    }

    private String requiredIdRef(Element element) {
        String result = idref(element);
        if (result == null) throw invalid("XMI_REFERENCE_MISSING", "xmi:idref is required");
        return result;
    }

    private String requiredId(Element element, String kind) {
        String result = id(element);
        if (result == null) throw invalid("XMI_ID_MISSING", kind + " has no xmi:id");
        return result;
    }

    private String requiredName(Element element, String kind) {
        String result = optionalName(element);
        if (result == null) throw invalid("XMI_NAME_MISSING", kind + " has no name: " + id(element));
        return result;
    }

    private String optionalName(Element element) { return attribute(element, "name"); }
    private String simpleType(String value) { return value == null ? null : value.substring(value.indexOf(':') + 1); }
    private String value(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
    private XmiImportException unresolved(String kind, String reference) {
        return invalid("XMI_REFERENCE_UNRESOLVED", "Unresolved " + kind + " reference: " + reference);
    }
    private XmiImportException invalid(String code, String message) {
        return new XmiImportException(code, HttpStatus.BAD_REQUEST, message);
    }

    private record RawEnd(String externalId, String roleName, String classifierExternalId,
            String visibility, int lower, Integer upper, boolean navigable,
            String xmiAggregation, String ownership) {
        AssociationEndData toData(String aggregation) {
            return new AssociationEndData(externalId, roleName, classifierExternalId, visibility,
                    lower, upper, navigable, aggregation, ownership);
        }
    }
}
