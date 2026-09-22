package com.casegen.interoperability.xmi;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import tools.jackson.databind.JsonNode;

/** Test-only canonicalization of the UML subset supported by CASEGen XMI. */
final class SemanticModelComparator {
    private SemanticModelComparator() {}

    static ComparisonResult compare(JsonNode expected, JsonNode actual, int unsupportedWarnings) {
        CanonicalUmlModel left = canonical(expected);
        CanonicalUmlModel right = canonical(actual);
        return new ComparisonResult(left.equals(right), left, right, unsupportedWarnings);
    }

    static void assertEquivalent(JsonNode expected, JsonNode actual) {
        ComparisonResult result = compare(expected, actual, 0);
        assertEquals(result.expected(), result.actual(), "Canonical UML semantic models differ");
    }

    static void assertEquivalent(JsonNode expected, XmiImportData actual) {
        assertEquals(canonical(expected), canonical(actual), "Canonical UML semantic models differ");
    }

    static CanonicalUmlModel canonical(JsonNode snapshot) {
        Context context = new Context(snapshot);
        List<CanonicalClassifier> classifiers = new ArrayList<>();
        for (JsonNode classifier : rows(snapshot, "classifiers")) {
            String id = text(classifier, "element_id");
            List<CanonicalProperty> properties = rows(snapshot, "properties").stream()
                    .filter(row -> id.equals(nullableText(row, "owner_classifier_id")))
                    .map(context::property).sorted(Comparator.comparing(CanonicalProperty::semanticKey)).toList();
            List<CanonicalOperation> operations = rows(snapshot, "operations").stream()
                    .filter(row -> id.equals(text(row, "owner_classifier_id")))
                    .map(context::operation).sorted(Comparator.comparing(CanonicalOperation::semanticKey)).toList();
            classifiers.add(new CanonicalClassifier(context.classifierName(id), text(classifier, "kind"),
                    upper(text(classifier, "visibility")), properties, operations));
        }
        classifiers.sort(Comparator.comparing(CanonicalClassifier::semanticKey));

        List<CanonicalAssociation> associations = rows(snapshot, "associations").stream()
                .map(context::association).sorted(Comparator.comparing(CanonicalAssociation::semanticKey)).toList();
        List<CanonicalGeneralization> generalizations = rows(snapshot, "generalizations").stream()
                .map(row -> new CanonicalGeneralization(
                        context.classifierName(text(row, "specific_classifier_id")),
                        context.classifierName(text(row, "general_classifier_id"))))
                .sorted(Comparator.comparing(CanonicalGeneralization::semanticKey)).toList();
        List<CanonicalRealization> realizations = rows(snapshot, "realizations").stream()
                .map(row -> new CanonicalRealization(
                        context.classifierName(text(row, "client_classifier_id")),
                        context.classifierName(text(row, "supplier_classifier_id"))))
                .sorted(Comparator.comparing(CanonicalRealization::semanticKey)).toList();
        return new CanonicalUmlModel(classifiers, associations, generalizations, realizations);
    }

    static CanonicalUmlModel canonical(XmiImportData data) {
        ImportContext context = new ImportContext(data);
        List<CanonicalClassifier> classifiers = data.classifiers().stream()
                .map(classifier -> {
                    List<CanonicalProperty> properties = data.properties().stream()
                            .filter(property -> classifier.externalId().equals(property.ownerClassifierExternalId()))
                            .map(context::property).sorted(Comparator.comparing(CanonicalProperty::semanticKey)).toList();
                    List<CanonicalOperation> operations = data.operations().stream()
                            .filter(operation -> classifier.externalId().equals(operation.ownerClassifierExternalId()))
                            .map(context::operation).sorted(Comparator.comparing(CanonicalOperation::semanticKey)).toList();
                    return new CanonicalClassifier(context.classifierName(classifier.externalId()),
                            upper(classifier.kind()), upper(classifier.visibility()), properties, operations);
                }).sorted(Comparator.comparing(CanonicalClassifier::semanticKey)).toList();
        List<CanonicalAssociation> associations = data.associations().stream()
                .map(context::association).sorted(Comparator.comparing(CanonicalAssociation::semanticKey)).toList();
        List<CanonicalGeneralization> generalizations = data.generalizations().stream()
                .map(value -> new CanonicalGeneralization(context.classifierName(value.specificExternalId()),
                        context.classifierName(value.generalExternalId())))
                .sorted(Comparator.comparing(CanonicalGeneralization::semanticKey)).toList();
        List<CanonicalRealization> realizations = data.realizations().stream()
                .map(value -> new CanonicalRealization(context.classifierName(value.clientExternalId()),
                        context.classifierName(value.supplierExternalId())))
                .sorted(Comparator.comparing(CanonicalRealization::semanticKey)).toList();
        return new CanonicalUmlModel(classifiers, associations, generalizations, realizations);
    }

    record ComparisonResult(boolean equivalent, CanonicalUmlModel expected,
            CanonicalUmlModel actual, int unsupportedWarningCount) {
        String status() { return equivalent ? "Round-trip supported subset: PASS" : "Round-trip supported subset: FAIL"; }
    }

    record CanonicalUmlModel(List<CanonicalClassifier> classifiers,
            List<CanonicalAssociation> associations,
            List<CanonicalGeneralization> generalizations,
            List<CanonicalRealization> realizations) {}

    record CanonicalClassifier(String name, String kind, String visibility,
            List<CanonicalProperty> properties, List<CanonicalOperation> operations) {
        String semanticKey() { return name + "|" + kind; }
    }

    record CanonicalProperty(String name, String visibility, String type,
            int lower, Integer upper) {
        String semanticKey() { return name + "|" + visibility + "|" + type + "|" + lower + "|" + upper; }
    }

    record CanonicalOperation(String name, String visibility, List<CanonicalParameter> parameters) {
        String semanticKey() { return name + "|" + visibility + "|" + parameters; }
    }

    record CanonicalParameter(String name, String direction, String type,
            int position, int lower, Integer upper) {}

    record CanonicalAssociation(String name, List<CanonicalAssociationEnd> ends) {
        String semanticKey() { return Objects.toString(name, "") + "|" + ends; }
    }

    record CanonicalAssociationEnd(String classifier, String roleName, String visibility,
            int lower, Integer upper, boolean navigable, String aggregation) {
        String semanticKey() {
            return classifier + "|" + Objects.toString(roleName, "") + "|" + visibility + "|"
                    + lower + "|" + upper + "|" + navigable + "|" + aggregation;
        }
    }

    record CanonicalGeneralization(String specific, String general) {
        String semanticKey() { return specific + "|" + general; }
    }

    record CanonicalRealization(String client, String supplier) {
        String semanticKey() { return client + "|" + supplier; }
    }

    private static final class Context {
        private final JsonNode snapshot;
        private final Map<String, JsonNode> elements = new HashMap<>();
        private final Map<String, JsonNode> packages = new HashMap<>();
        private final Map<String, JsonNode> properties = new HashMap<>();

        Context(JsonNode snapshot) {
            this.snapshot = snapshot;
            rows(snapshot, "elements").forEach(row -> elements.put(text(row, "id"), row));
            rows(snapshot, "packages").forEach(row -> packages.put(text(row, "element_id"), row));
            rows(snapshot, "properties").forEach(row -> properties.put(text(row, "element_id"), row));
        }

        String classifierName(String id) {
            JsonNode classifier = rows(snapshot, "classifiers").stream()
                    .filter(row -> id.equals(text(row, "element_id"))).findFirst().orElseThrow();
            String packageId = nullableText(classifier, "package_id");
            String prefix = packageId == null ? "" : packageName(packageId) + "::";
            return prefix + text(elements.get(id), "name");
        }

        private String packageName(String id) {
            JsonNode row = packages.get(id);
            String parent = nullableText(row, "parent_package_id");
            String own = text(elements.get(id), "name");
            return parent == null ? own : packageName(parent) + "::" + own;
        }

        CanonicalProperty property(JsonNode row) {
            return new CanonicalProperty(nullableText(elements.get(text(row, "element_id")), "name"),
                    upper(text(row, "visibility")), type(row), integer(row, "lower_bound"),
                    nullableInteger(row, "upper_bound"));
        }

        CanonicalOperation operation(JsonNode row) {
            String id = text(row, "element_id");
            List<CanonicalParameter> parameters = rows(snapshot, "parameters").stream()
                    .filter(parameter -> id.equals(text(parameter, "operation_id")))
                    .map(parameter -> new CanonicalParameter(
                            nullableText(elements.get(text(parameter, "element_id")), "name"),
                            upper(text(parameter, "direction")), type(parameter), integer(parameter, "position"),
                            integer(parameter, "lower_bound"), nullableInteger(parameter, "upper_bound")))
                    .sorted(Comparator.comparingInt(CanonicalParameter::position)
                            .thenComparing(value -> Objects.toString(value.name(), ""))
                            .thenComparing(CanonicalParameter::direction)).toList();
            return new CanonicalOperation(nullableText(elements.get(id), "name"),
                    upper(text(row, "visibility")), parameters);
        }

        CanonicalAssociation association(JsonNode row) {
            String id = text(row, "element_id");
            List<CanonicalAssociationEnd> ends = rows(snapshot, "associationEnds").stream()
                    .filter(end -> id.equals(text(end, "association_id")))
                    .map(end -> {
                        JsonNode property = properties.get(text(end, "property_id"));
                        return new CanonicalAssociationEnd(
                                classifierName(text(property, "type_classifier_id")),
                                nullableText(elements.get(text(end, "property_id")), "name"),
                                upper(text(property, "visibility")), integer(property, "lower_bound"),
                                nullableInteger(property, "upper_bound"), end.path("navigable").asBoolean(),
                                upper(text(end, "aggregation")));
                    }).sorted(Comparator.comparing(CanonicalAssociationEnd::semanticKey)).toList();
            return new CanonicalAssociation(nullableText(elements.get(id), "name"), ends);
        }

        private String type(JsonNode row) {
            return "PRIMITIVE".equals(text(row, "type_kind"))
                    ? "PRIMITIVE:" + text(row, "primitive_type")
                    : "CLASSIFIER:" + classifierName(text(row, "type_classifier_id"));
        }
    }

    private static final class ImportContext {
        private final XmiImportData data;
        private final Map<String, XmiImportData.PackageData> packages = new HashMap<>();
        private final Map<String, XmiImportData.ClassifierData> classifiers = new HashMap<>();

        ImportContext(XmiImportData data) {
            this.data = data;
            data.packages().forEach(value -> packages.put(value.externalId(), value));
            data.classifiers().forEach(value -> classifiers.put(value.externalId(), value));
        }

        String classifierName(String externalId) {
            XmiImportData.ClassifierData classifier = required(classifiers.get(externalId), externalId);
            String prefix = classifier.packageExternalId() == null
                    ? "" : packageName(classifier.packageExternalId()) + "::";
            return prefix + classifier.name();
        }

        private String packageName(String externalId) {
            XmiImportData.PackageData value = required(packages.get(externalId), externalId);
            return value.parentExternalId() == null ? value.name()
                    : packageName(value.parentExternalId()) + "::" + value.name();
        }

        CanonicalProperty property(XmiImportData.PropertyData value) {
            return new CanonicalProperty(value.name(), upper(value.visibility()), type(value.type()),
                    value.lower(), value.upper());
        }

        CanonicalOperation operation(XmiImportData.OperationData value) {
            List<CanonicalParameter> parameters = data.parameters().stream()
                    .filter(parameter -> value.externalId().equals(parameter.operationExternalId()))
                    .map(parameter -> new CanonicalParameter(parameter.name(), upper(parameter.direction()),
                            type(parameter.type()), parameter.position(), parameter.lower(), parameter.upper()))
                    .sorted(Comparator.comparingInt(CanonicalParameter::position)
                            .thenComparing(parameter -> Objects.toString(parameter.name(), ""))
                            .thenComparing(CanonicalParameter::direction)).toList();
            return new CanonicalOperation(value.name(), upper(value.visibility()), parameters);
        }

        CanonicalAssociation association(XmiImportData.AssociationData value) {
            List<CanonicalAssociationEnd> ends = List.of(end(value.first()), end(value.second())).stream()
                    .sorted(Comparator.comparing(CanonicalAssociationEnd::semanticKey)).toList();
            return new CanonicalAssociation(value.name(), ends);
        }

        private CanonicalAssociationEnd end(XmiImportData.AssociationEndData value) {
            return new CanonicalAssociationEnd(classifierName(value.classifierExternalId()), value.roleName(),
                    upper(value.visibility()), value.lower(), value.upper(), value.navigable(),
                    upper(value.aggregation()));
        }

        private String type(XmiImportData.TypedValue value) {
            return "PRIMITIVE".equals(value.typeKind())
                    ? "PRIMITIVE:" + value.primitiveType()
                    : "CLASSIFIER:" + classifierName(value.classifierExternalId());
        }

        private <T> T required(T value, String externalId) {
            if (value == null) throw new IllegalArgumentException("Unresolved canonical external id " + externalId);
            return value;
        }
    }

    private static List<JsonNode> rows(JsonNode snapshot, String field) {
        List<JsonNode> result = new ArrayList<>();
        snapshot.path(field).forEach(result::add);
        return result;
    }

    private static String text(JsonNode row, String field) {
        String result = nullableText(row, field);
        if (result == null) throw new IllegalArgumentException("Missing canonical field " + field);
        return result;
    }

    private static String nullableText(JsonNode row, String field) {
        JsonNode value = row == null ? null : row.path(field);
        return value == null || value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private static int integer(JsonNode row, String field) { return row.path(field).asInt(); }
    private static Integer nullableInteger(JsonNode row, String field) {
        JsonNode value = row.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asInt();
    }
    private static String upper(String value) { return value.toUpperCase(java.util.Locale.ROOT); }
}
