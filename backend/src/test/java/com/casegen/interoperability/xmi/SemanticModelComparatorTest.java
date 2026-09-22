package com.casegen.interoperability.xmi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class SemanticModelComparatorTest {
    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void ignoresInternalIdentityOrderAndNonSemanticMetadata() throws Exception {
        JsonNode first = snapshot("classifier-a", "property-a", "package-a", "Customer", 1);
        JsonNode second = snapshot("new-classifier-id", "new-property-id", "new-package-id", "Customer", 1);

        var result = SemanticModelComparator.compare(first, second, 3);

        assertTrue(result.equivalent());
        assertEquals("Round-trip supported subset: PASS", result.status());
        assertEquals(3, result.unsupportedWarningCount());
    }

    @Test
    void reportsARealSemanticDifference() throws Exception {
        JsonNode first = snapshot("classifier-a", "property-a", "package-a", "Customer", 1);
        JsonNode changedMultiplicity = snapshot("classifier-b", "property-b", "package-b", "Customer", 0);

        var result = SemanticModelComparator.compare(first, changedMultiplicity, 0);

        assertFalse(result.equivalent());
        assertEquals("Round-trip supported subset: FAIL", result.status());
    }

    private JsonNode snapshot(String classifierId, String propertyId, String packageId,
            String classifierName, int lower) throws Exception {
        return json.readTree("""
                {
                  "model":{"id":"ignored","name":"Ignored","revision":99},
                  "elements":[
                    {"id":"%s","element_type":"PROPERTY","name":"name","external_xmi_id":"ignored-property"},
                    {"id":"%s","element_type":"CLASSIFIER","name":"%s","external_xmi_id":"ignored-classifier"},
                    {"id":"%s","element_type":"PACKAGE","name":"sales","external_xmi_id":"ignored-package"}
                  ],
                  "packages":[{"element_id":"%s","parent_package_id":null}],
                  "classifiers":[{"element_id":"%s","package_id":"%s","kind":"CLASS","visibility":"PUBLIC"}],
                  "properties":[{"element_id":"%s","owner_classifier_id":"%s","visibility":"PRIVATE",
                    "type_kind":"PRIMITIVE","primitive_type":"STRING","type_classifier_id":null,
                    "lower_bound":%d,"upper_bound":1}],
                  "operations":[],"parameters":[],"associations":[],"associationEnds":[],
                  "generalizations":[],"realizations":[],
                  "diagram":{"revision":123,"nodes":[{"x":500,"y":700}]}
                }
                """.formatted(propertyId, classifierId, classifierName, packageId, packageId,
                classifierId, packageId, propertyId, classifierId, lower));
    }
}
