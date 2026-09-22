package com.casegen.validation.service;

import com.casegen.validation.dto.ValidationIssueResponse;
import java.util.*;
import javax.lang.model.SourceVersion;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/** Deterministic validation over detached semantic data, with no mutations. */
@Component
public class SemanticValidationEngine {
    private static final Set<String> PRIMITIVES = Set.of("STRING", "INTEGER", "BOOLEAN", "REAL", "UNLIMITED_NATURAL");

    public List<ValidationIssueResponse> validate(JsonNode snapshot) {
        List<ValidationIssueResponse> issues = new ArrayList<>();
        Map<String, JsonNode> elements = index(snapshot.path("elements"), "id");
        Map<String, JsonNode> classifiers = index(snapshot.path("classifiers"), "element_id");
        Map<String, JsonNode> properties = index(snapshot.path("properties"), "element_id");
        Map<String, JsonNode> operations = index(snapshot.path("operations"), "element_id");
        Map<String, JsonNode> packages = index(snapshot.path("packages"), "element_id");
        Map<String, JsonNode> associations = index(snapshot.path("associations"), "element_id");
        Map<String, JsonNode> ends = index(snapshot.path("associationEnds"), "property_id");
        Map<String, Set<String>> names = new HashMap<>();

        for (JsonNode e : snapshot.path("elements")) {
            String id = text(e, "id"), kind = text(e, "element_type"), name = text(e, "name");
            boolean optional = Set.of("ASSOCIATION", "GENERALIZATION", "REALIZATION").contains(kind)
                    || (kind.equals("PROPERTY") && ends.containsKey(id))
                    || (kind.equals("PARAMETER") && hasReturn(snapshot, id));
            if ((!optional && name.isBlank()) || name.length() > 180 || name.chars().anyMatch(Character::isISOControl))
                issue(issues, id, "INVALID_NAME", "Element name is blank, too long or contains control characters", "/elements/" + id + "/name");
            String table = switch (kind) {
                case "PACKAGE" -> "packages"; case "CLASSIFIER" -> "classifiers"; case "PROPERTY" -> "properties";
                case "OPERATION" -> "operations"; case "PARAMETER" -> "parameters"; case "ASSOCIATION" -> "associations";
                case "GENERALIZATION" -> "generalizations"; case "REALIZATION" -> "realizations"; default -> "";
            };
            if (table.isEmpty() || !index(snapshot.path(table), "element_id").containsKey(id))
                issue(issues, id, "MISSING_SPECIALIZATION", "Element has no supported semantic specialization", "/elements/" + id);
        }
        for (JsonNode c : snapshot.path("classifiers")) {
            String id = text(c, "element_id");
            unique(issues, names, "namespace:" + text(c, "package_id"), elements.get(id), id);
            reference(issues, id, c, "package_id", packages, false);
        }
        for (JsonNode p : snapshot.path("packages")) {
            String id = text(p, "element_id");
            unique(issues, names, "namespace:" + text(p, "parent_package_id"), elements.get(id), id);
            reference(issues, id, p, "parent_package_id", packages, false);
        }
        Map<String, List<String>> packageGraph = new HashMap<>();
        for (JsonNode p : snapshot.path("packages")) if (!text(p, "parent_package_id").isEmpty())
            packageGraph.computeIfAbsent(text(p, "element_id"), k -> new ArrayList<>()).add(text(p, "parent_package_id"));
        checkCycles(issues, packageGraph, "PACKAGE_CYCLE");
        for (JsonNode p : snapshot.path("properties")) {
            String id = text(p, "element_id");
            boolean associationEnd = ends.containsKey(id);
            reference(issues, id, p, "owner_classifier_id", classifiers, !associationEnd);
            if (!associationEnd) unique(issues, names, "property:" + text(p, "owner_classifier_id"), elements.get(id), id);
            validateType(issues, p, classifiers);
        }
        Set<String> signatures = new HashSet<>();
        for (JsonNode o : snapshot.path("operations")) {
            String id = text(o, "element_id");
            reference(issues, id, o, "owner_classifier_id", classifiers, true);
            List<JsonNode> params = new ArrayList<>();
            for (JsonNode p : snapshot.path("parameters")) if (text(p, "operation_id").equals(id) && !text(p, "direction").equals("RETURN")) params.add(p);
            params.sort(Comparator.comparingInt(p -> p.path("position").asInt()));
            String signature = text(o, "owner_classifier_id") + ":" + text(elements.get(id), "name").toLowerCase(Locale.ROOT)
                    + params.stream().map(p -> text(p,"direction") + ":" + text(p,"type_kind") + ":" + text(p,"primitive_type") + ":" + text(p,"type_classifier_id")).toList();
            if (!signatures.add(signature)) issue(issues,id,"DUPLICATE_OPERATION_SIGNATURE","Operation overload has a duplicate signature","/operations/"+id);
        }
        Map<String, Integer> returns = new HashMap<>();
        Set<String> positions = new HashSet<>();
        for (JsonNode p : snapshot.path("parameters")) {
            String id = text(p, "element_id"), operation = text(p, "operation_id");
            reference(issues, id, p, "operation_id", operations, true);
            if (!Set.of("IN","OUT","INOUT","RETURN").contains(text(p,"direction"))) issue(issues,id,"INVALID_PARAMETER_DIRECTION","Unsupported parameter direction","/parameters/"+id);
            validateType(issues, p, classifiers);
            if (text(p, "direction").equals("RETURN")) {
                if (returns.merge(operation, 1, Integer::sum) > 1) issue(issues, id, "MULTIPLE_RETURN_PARAMETERS", "Operation has more than one RETURN", "/parameters/" + id);
            } else unique(issues, names, "parameter:" + operation, elements.get(id), id);
            if (p.path("position").asInt(-1) < 0 || !positions.add(operation + ":" + p.path("position").asInt()))
                issue(issues, id, "INVALID_PARAMETER_POSITION", "Parameter positions must be nonnegative and unique", "/parameters/" + id);
        }
        for (JsonNode a : snapshot.path("associations")) {
            String id = text(a, "element_id");
            reference(issues, id, a, "package_id", packages, false);
            List<JsonNode> members = new ArrayList<>();
            for (JsonNode end : snapshot.path("associationEnds")) if (text(end, "association_id").equals(id)) members.add(end);
            if (members.size() != 2 || members.stream().map(e -> e.path("member_order").asInt()).distinct().count() != 2
                    || members.stream().anyMatch(e -> e.path("member_order").asInt() < 1 || e.path("member_order").asInt() > 2))
                issue(issues, id, "ASSOCIATION_END_COUNT", "Association must have exactly two ordered ends", "/associations/" + id);
            long aggregates = members.stream().filter(e -> !text(e, "aggregation").equals("NONE")).count();
            if (aggregates > 1) issue(issues, id, "INVALID_AGGREGATION", "Only one end can be the whole", "/associations/" + id);
        }
        for (JsonNode end : snapshot.path("associationEnds")) {
            String id = text(end, "property_id");
            reference(issues, id, end, "property_id", properties, true);
            reference(issues, id, end, "association_id", associations, true);
            JsonNode property = properties.get(id);
            if (property == null) continue;
            if (!text(property, "type_kind").equals("CLASSIFIER")) issue(issues, id, "INVALID_ASSOCIATION_END_TYPE", "Association ends must have Classifier type", "/associationEnds/" + id);
            if (text(end, "ownership").equals("ASSOCIATION") && !text(property, "owner_classifier_id").isEmpty()
                    || text(end, "ownership").equals("CLASSIFIER") && text(property, "owner_classifier_id").isEmpty())
                issue(issues, id, "INVALID_END_OWNERSHIP", "End ownership is inconsistent", "/associationEnds/" + id);
            if (text(end, "aggregation").equals("COMPOSITE") && (property.path("upper_bound").isNull() || property.path("upper_bound").asInt() > 1))
                issue(issues, id, "INVALID_COMPOSITE_MULTIPLICITY", "Composite whole must have upper <= 1", "/associationEnds/" + id);
        }
        Map<String, List<String>> graph = new HashMap<>();
        for (JsonNode g : snapshot.path("generalizations")) {
            String id = text(g, "element_id"), specific = text(g, "specific_classifier_id"), general = text(g, "general_classifier_id");
            reference(issues, id, g, "specific_classifier_id", classifiers, true);
            reference(issues, id, g, "general_classifier_id", classifiers, true);
            graph.computeIfAbsent(specific, k -> new ArrayList<>()).add(general);
            if (classifiers.containsKey(specific) && classifiers.containsKey(general)
                    && !text(classifiers.get(specific), "kind").equals(text(classifiers.get(general), "kind")))
                issue(issues, id, "INVALID_GENERALIZATION_KIND", "Generalization requires classifiers of the same kind", "/generalizations/" + id);
        }
        checkCycles(issues, graph, "GENERALIZATION_CYCLE");
        for (JsonNode r : snapshot.path("realizations")) {
            String id = text(r, "element_id");
            reference(issues, id, r, "client_classifier_id", classifiers, true);
            reference(issues, id, r, "supplier_classifier_id", classifiers, true);
            if (!"CLASS".equals(text(classifiers.get(text(r, "client_classifier_id")), "kind"))
                    || !"INTERFACE".equals(text(classifiers.get(text(r, "supplier_classifier_id")), "kind")))
                issue(issues, id, "INVALID_REALIZATION", "Realization requires CLASS -> INTERFACE", "/realizations/" + id);
        }
        validateGeneration(snapshot, issues, elements, classifiers, properties);
        return List.copyOf(issues);
    }

    private void validateGeneration(JsonNode snapshot, List<ValidationIssueResponse> issues, Map<String, JsonNode> elements,
            Map<String, JsonNode> classifiers, Map<String, JsonNode> properties) {
        JsonNode generation = snapshot.path("generation");
        Map<String, JsonNode> profiles = index(generation.path("profiles"), "id");
        Map<String, Map<String, JsonNode>> mappings = new HashMap<>();
        for (JsonNode m : generation.path("mappings")) {
            String id = text(m, "uml_element_id"), profile = text(m, "generation_profile_id"), kind = text(m, "target_kind");
            if (!profiles.containsKey(profile) || !elements.containsKey(id) || !classifiers.containsKey(id)
                    || !Set.of("ENTITY", "VALUE_OBJECT", "SERVICE", "JAVA_INTERFACE", "IGNORE").contains(kind)) {
                issue(issues, elements.containsKey(id) ? id : null, "INVALID_GENERATION_MAPPING", "Mapping must target a classifier in this model and a valid profile", "/generation/mappings");
                continue;
            }
            Map<String, JsonNode> byElement = mappings.computeIfAbsent(profile, k -> new HashMap<>());
            if (byElement.put(id, m) != null) issue(issues, id, "DUPLICATE_MAPPING", "Classifier mapped twice in one profile", "/generation/mappings");
            String classifierKind = text(classifiers.get(id), "kind");
            if ((kind.equals("JAVA_INTERFACE") && !classifierKind.equals("INTERFACE"))
                    || (!Set.of("JAVA_INTERFACE", "IGNORE").contains(kind) && !classifierKind.equals("CLASS")))
                issue(issues, id, "INVALID_MAPPING_KIND", "Mapping kind is incompatible with UML classifier", "/generation/mappings");
            if (!kind.equals("IGNORE") && !javaIdentifier(text(elements.get(id), "name")))
                issue(issues, id, "INVALID_JAVA_NAME", "Classifier name cannot be mapped to Java", "/generation/mappings");
            if (kind.equals("ENTITY")) {
                String configured = m.path("config_json").path("idPropertyId").asText(m.path("config_json").path("identifierPropertyId").asText(""));
                JsonNode identifier = null;
                for (JsonNode p : properties.values()) if ((text(p, "owner_classifier_id").equals(id)
                        || (!configured.isEmpty() && inheritedOwner(snapshot,id,text(p,"owner_classifier_id"))))
                        && (configured.isEmpty() ? text(elements.get(text(p, "element_id")), "name").equalsIgnoreCase("id") : configured.equals(text(p, "element_id")))) identifier = p;
                if (identifier == null || !text(identifier, "type_kind").equals("PRIMITIVE") || identifier.path("lower_bound").asInt() != 1
                        || identifier.path("upper_bound").isNull() || identifier.path("upper_bound").asInt() != 1
                        || identifier.path("is_static").asBoolean() || identifier.path("is_derived").asBoolean()
                        || !Set.of("STRING", "INTEGER", "UNLIMITED_NATURAL").contains(text(identifier, "primitive_type")))
                    issue(issues, id, "ENTITY_IDENTIFIER_REQUIRED", "ENTITY requires a scalar required String/Integer/UnlimitedNatural identifier; configure identifierPropertyId or an id property", "/generation/mappings/" + id);
            }
        }
        if (profiles.isEmpty()) issues.add(new ValidationIssueResponse(UUID.randomUUID(), null, "WARNING", "NO_GENERATION_PROFILE", "Semantic version is allowed; no generation profile configured", "/generation"));
        for (JsonNode profile : profiles.values()) {
            String id = text(profile, "id");
            if (!text(profile, "target").equals("SPRING_BOOT") || !text(profile, "database").equals("POSTGRESQL")
                    || profile.path("java_version").asInt() < 21 || !javaPackage(text(profile, "group_id"))
                    || !javaPackage(text(profile, "base_package")) || !text(profile, "artifact_id").matches("[A-Za-z0-9][A-Za-z0-9._-]*")
                    || !text(profile, "spring_boot_version").matches("\\d+\\.\\d+\\.\\d+(?:[-.][A-Za-z0-9.-]+)?"))
                issue(issues, null, "INVALID_GENERATION_PROFILE", "Invalid Spring Boot target coordinates", "/generation/profiles/" + id);
            Map<String, JsonNode> mapped = mappings.getOrDefault(id, Map.of());
            if (mapped.isEmpty()) issues.add(new ValidationIssueResponse(UUID.randomUUID(), null, "WARNING", "NO_ELEMENT_MAPPINGS", "Profile has no element mappings", "/generation/profiles/" + id));
            for (JsonNode p : properties.values()) {
                JsonNode owner = mapped.get(text(p, "owner_classifier_id"));
                if (owner == null || text(owner, "target_kind").equals("IGNORE")) continue;
                String propertyId = text(p, "element_id");
                if (!javaIdentifier(text(elements.get(propertyId), "name"))) issue(issues, propertyId, "INVALID_JAVA_NAME", "Property name cannot be mapped to Java", "/generation/properties/" + propertyId);
                if (text(p, "type_kind").equals("CLASSIFIER")) {
                    JsonNode type = mapped.get(text(p, "type_classifier_id"));
                    if (type == null || !Set.of("ENTITY", "VALUE_OBJECT").contains(text(type, "target_kind")))
                        issue(issues, propertyId, "UNMAPPABLE_CLASSIFIER_TYPE", "Property type needs an ENTITY or VALUE_OBJECT mapping in the same profile", "/generation/properties/" + propertyId);
                }
            }
            Map<String, JsonNode> operations = index(snapshot.path("operations"), "element_id");
            for (JsonNode o : operations.values()) {
                JsonNode owner = mapped.get(text(o,"owner_classifier_id"));
                if (owner != null && !text(owner,"target_kind").equals("IGNORE") && !javaIdentifier(text(elements.get(text(o,"element_id")),"name")))
                    issue(issues,text(o,"element_id"),"INVALID_JAVA_NAME","Operation name cannot be mapped to Java","/generation/operations");
            }
            for (JsonNode parameter : snapshot.path("parameters")) {
                JsonNode operation = operations.get(text(parameter,"operation_id"));
                JsonNode owner = mapped.get(text(operation,"owner_classifier_id"));
                if (owner == null || text(owner,"target_kind").equals("IGNORE")) continue;
                String parameterId = text(parameter,"element_id");
                if (!text(parameter,"direction").equals("RETURN") && !javaIdentifier(text(elements.get(parameterId),"name")))
                    issue(issues,parameterId,"INVALID_JAVA_NAME","Parameter name cannot be mapped to Java","/generation/parameters/"+parameterId);
                JsonNode type = mapped.get(text(parameter,"type_classifier_id"));
                if (text(parameter,"type_kind").equals("CLASSIFIER") && (type == null || text(type,"target_kind").equals("IGNORE")))
                    issue(issues,parameterId,"UNMAPPABLE_CLASSIFIER_TYPE","Parameter type requires a Java mapping in the same profile","/generation/parameters/"+parameterId);
            }
        }
    }

    private void validateType(List<ValidationIssueResponse> issues, JsonNode p, Map<String, JsonNode> classifiers) {
        String id = text(p, "element_id"), kind = text(p, "type_kind");
        boolean primitive = kind.equals("PRIMITIVE") && PRIMITIVES.contains(text(p, "primitive_type")) && text(p, "type_classifier_id").isEmpty();
        boolean classifier = kind.equals("CLASSIFIER") && text(p, "primitive_type").isEmpty() && !text(p, "type_classifier_id").isEmpty();
        if (!primitive && !classifier) issue(issues, id, "INVALID_TYPE", "Type requires exactly one supported primitive or classifier", "/types/" + id);
        if (classifier) reference(issues, id, p, "type_classifier_id", classifiers, true);
        if (p.path("lower_bound").asInt(-1) < 0 || (!p.path("upper_bound").isNull() && p.path("upper_bound").asInt(-1) < p.path("lower_bound").asInt()))
            issue(issues, id, "INVALID_MULTIPLICITY", "Multiplicity requires 0 <= lower <= upper, or unlimited upper", "/types/" + id);
    }
    private boolean hasReturn(JsonNode snapshot, String id) {
        for (JsonNode p : snapshot.path("parameters")) if (text(p, "element_id").equals(id) && text(p, "direction").equals("RETURN")) return true;
        return false;
    }
    private void unique(List<ValidationIssueResponse> issues, Map<String, Set<String>> names, String scope, JsonNode element, String id) {
        String name = text(element, "name").toLowerCase(Locale.ROOT);
        if (!name.isEmpty() && !names.computeIfAbsent(scope, k -> new HashSet<>()).add(name)) issue(issues, id, "DUPLICATE_NAME", "Duplicate name in the same owner/package", "/elements/" + id + "/name");
    }
    private void reference(List<ValidationIssueResponse> issues, String id, JsonNode node, String field, Map<String, JsonNode> targets, boolean required) {
        String value = text(node, field);
        if ((required || !value.isEmpty()) && !targets.containsKey(value)) issue(issues, id, "CROSS_MODEL_OR_MISSING_REFERENCE", "Reference " + field + " must belong to this model", "/elements/" + id + "/" + field);
    }
    private void checkCycles(List<ValidationIssueResponse> issues, Map<String, List<String>> graph, String code) {
        for (String start : new TreeSet<>(graph.keySet())) if (cycle(start, graph, new HashSet<>(), new HashSet<>())) {
            issue(issues, start, code, "Inheritance/package graph contains a cycle", "/elements/" + start); break;
        }
    }
    private boolean cycle(String node, Map<String, List<String>> graph, Set<String> active, Set<String> visited) {
        if (active.contains(node)) return true;
        if (!visited.add(node)) return false;
        active.add(node);
        for (String next : graph.getOrDefault(node, List.of())) if (cycle(next, graph, active, visited)) return true;
        active.remove(node); return false;
    }

    private boolean inheritedOwner(JsonNode snapshot,String classifier,String owner) {
        Set<String> seen=new HashSet<>();Deque<String> pending=new ArrayDeque<>();pending.add(classifier);
        while (!pending.isEmpty()) {
            String next=pending.removeFirst();if (!seen.add(next)) continue;
            if (next.equals(owner)) return true;
            for (JsonNode g:snapshot.path("generalizations")) if (text(g,"specific_classifier_id").equals(next))
                pending.add(text(g,"general_classifier_id"));
        }
        return false;
    }
    private static Map<String, JsonNode> index(JsonNode nodes, String key) {
        Map<String, JsonNode> result = new LinkedHashMap<>();
        for (JsonNode node : nodes) result.put(text(node, key), node);
        return result;
    }
    private static String text(JsonNode node, String field) { return node == null ? "" : node.path(field).asText(""); }
    public static boolean javaIdentifier(String name) { return SourceVersion.isIdentifier(name) && !SourceVersion.isKeyword(name); }
    public static boolean javaPackage(String name) { return !name.isBlank() && Arrays.stream(name.split("\\.", -1)).allMatch(SemanticValidationEngine::javaIdentifier); }
    private static void issue(List<ValidationIssueResponse> issues, String id, String code, String message, String path) {
        issues.add(new ValidationIssueResponse(UUID.randomUUID(), id == null ? null : UUID.fromString(id), "ERROR", code, message, path));
    }
}
