package com.casegen.interoperability.image;

import java.util.List;
import java.util.Map;

final class UmlImageAnalysisContract {
    static final String PROMPT = """
            You are a visual analyzer specialized exclusively in UML class diagrams.
            Analyze the COMPLETE original image and its global diagram structure before reading text.
            Return only JSON matching the supplied schema: no Markdown, prose, or fenced code.

            CLASSIFIERS AND COMPARTMENTS:
            A UML Class or Interface can be one outer rectangular block made of one, two, or three
            vertically contiguous compartments with approximately equal width and continuous borders:
            (1) name/stereotype, (2) attributes, and (3) operations. Empty compartments are valid.
            All contiguous compartments belong to ONE classifier. Whitespace between separate outer
            boxes normally separates classifiers. First identify outer classifier boxes, then read them.
            A classifier name must come only from the top/header compartment. Text such as
            "date : date", "quantity", "name : String", "calcTotal()", or "authorized()" inside
            attribute/operation compartments must NEVER become a classifier.
            Recognize <<interface>> or «interface» and return kind=INTERFACE with the stereotype removed
            from its name.

            MEMBERS:
            Attribute syntax is [visibility] name [: type]. Operation syntax is
            [visibility] name(parameters) [: returnType]. Visibility symbols map as follows:
            +=PUBLIC, -=PRIVATE, #=PROTECTED, ~=PACKAGE. If no symbol is legible, use UNKNOWN so CASEGen
            can apply its existing default. Do not convert attributes or operations into classifiers.

            RELATIONSHIPS:
            Reconstruct each complete semantic relationship even if its graphical line has multiple
            orthogonal segments or 90-degree bends; bends never create extra relationships.
            Solid line=ASSOCIATION, hollow diamond=AGGREGATION, filled diamond=COMPOSITION,
            solid line+hollow triangle=GENERALIZATION, dashed line+hollow triangle=REALIZATION.
            An arrow on an association end means navigable when clearly visible.
            For aggregation/composition, source is the whole/diamond end.
            A branched generalization connector represents one relationship from EACH child to the same
            parent; shared line segments do not create child-to-child relationships.
            Read multiplicities only when adjacent to relationship endpoints: 1, *, 0..1, 0..*, 1..*.
            Preserve the source/target orientation used by this schema.

            ACCURACY:
            Detect Class, Interface, attributes, operations, parameters, return types, visibility,
            Association, Aggregation, Composition, Generalization, Realization, multiplicities, roles,
            and navigability only when visible. Never invent illegible information. For a geometrically
            confirmed classifier whose header is unreadable, use a neutral name such as Clase1,
            confidence about 0.35, and a warning explaining that the name must be reviewed.
            Use UNKNOWN or null only where the schema permits it and add a warning for ambiguity.
            Do not merge classifiers merely because their text is similar.

            CONTRACT:
            Use stable local ids (c1, a1, o1, p1, r1). Relationship source/target and classifier types
            must reference classifier ids. Primitive type values are STRING, INTEGER, BOOLEAN, REAL,
            UNLIMITED_NATURAL. An unbounded upper multiplicity is upperBound=null and
            upperUnlimited=true. Return types use VOID only when the diagram explicitly has no return.
            """;

    private UmlImageAnalysisContract() {}

    static Map<String, Object> schema() {
        Map<String, Object> string = Map.of("type", "string");
        Map<String, Object> integerOrNull = Map.of("type", List.of("integer", "null"));
        Map<String, Object> number = Map.of("type", "number", "minimum", 0, "maximum", 1);
        Map<String, Object> type = object(Map.of("kind", string, "value", string), "kind", "value");
        Map<String, Object> parameter = object(Map.of(
                "id", string, "name", string, "direction", string, "type", type,
                "position", Map.of("type", "integer"), "lowerBound", integerOrNull,
                "upperBound", integerOrNull, "upperUnlimited", Map.of("type", "boolean"),
                "multiplicityKnown", Map.of("type", "boolean"), "confidence", number),
                "id", "name", "direction", "type", "position", "lowerBound", "upperBound",
                "upperUnlimited", "multiplicityKnown", "confidence");
        Map<String, Object> end = object(Map.of(
                "classifierId", string, "roleName", string, "visibility", string,
                "lowerBound", integerOrNull, "upperBound", integerOrNull,
                "upperUnlimited", Map.of("type", "boolean"),
                "multiplicityKnown", Map.of("type", "boolean"),
                "navigable", Map.of("type", "boolean")),
                "classifierId", "roleName", "visibility", "lowerBound", "upperBound",
                "upperUnlimited", "multiplicityKnown", "navigable");
        return object(Map.of(
                "suggestedModelName", string,
                "detectedClassifiers", array(object(Map.of("id", string, "name", string,
                        "kind", string, "visibility", string, "confidence", number),
                        "id", "name", "kind", "visibility", "confidence")),
                "detectedAttributes", array(object(Map.of("id", string, "ownerClassifierId", string,
                        "name", string, "visibility", string, "type", type,
                        "lowerBound", integerOrNull, "upperBound", integerOrNull,
                        "upperUnlimited", Map.of("type", "boolean"),
                        "multiplicityKnown", Map.of("type", "boolean"), "confidence", number),
                        "id", "ownerClassifierId", "name", "visibility", "type", "lowerBound",
                        "upperBound", "upperUnlimited", "multiplicityKnown", "confidence")),
                "detectedOperations", array(object(Map.of("id", string, "ownerClassifierId", string,
                        "name", string, "visibility", string, "parameters", array(parameter),
                        "returnType", type, "confidence", number),
                        "id", "ownerClassifierId", "name", "visibility", "parameters", "returnType", "confidence")),
                "detectedRelationships", array(object(Map.of("id", string, "type", string,
                        "name", string, "source", end, "target", end, "confidence", number),
                        "id", "type", "name", "source", "target", "confidence")),
                "warnings", array(object(Map.of("code", string, "message", string,
                        "elementId", Map.of("type", List.of("string", "null"))),
                        "code", "message", "elementId")),
                "confidence", number),
                "suggestedModelName", "detectedClassifiers", "detectedAttributes",
                "detectedOperations", "detectedRelationships", "warnings", "confidence");
    }

    private static Map<String, Object> array(Map<String, Object> items) {
        return Map.of("type", "array", "items", items);
    }

    private static Map<String, Object> object(Map<String, Object> properties, String... required) {
        return Map.of("type", "object", "additionalProperties", false,
                "properties", properties, "required", List.of(required));
    }
}
