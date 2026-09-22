package com.casegen.validation;

import static org.junit.jupiter.api.Assertions.*;
import com.casegen.validation.service.SemanticValidationEngine;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

class SemanticValidationEngineTest {
    private final JsonMapper json = JsonMapper.builder().build();
    private final SemanticValidationEngine engine = new SemanticValidationEngine();
    private final String first = UUID.randomUUID().toString();
    private final String second = UUID.randomUUID().toString();
    private final String property = UUID.randomUUID().toString();
    private final String operation = UUID.randomUUID().toString();
    private ObjectNode base() {
        ObjectNode s = json.createObjectNode();
        for (String key : new String[]{"elements","packages","classifiers","properties","operations","parameters","associations","associationEnds","generalizations","realizations"}) s.putArray(key);
        s.putObject("generation").putArray("profiles"); ((ObjectNode)s.path("generation")).putArray("mappings");
        element(s,first,"CLASSIFIER","Customer"); element(s,second,"CLASSIFIER","Person");
        row(s,"classifiers").put("element_id",first).put("kind","CLASS");
        row(s,"classifiers").put("element_id",second).put("kind","CLASS");
        element(s,property,"PROPERTY","id");
        row(s,"properties").put("element_id",property).put("owner_classifier_id",first).put("type_kind","PRIMITIVE")
                .put("primitive_type","STRING").put("lower_bound",1).put("upper_bound",1);
        element(s,operation,"OPERATION","register"); row(s,"operations").put("element_id",operation).put("owner_classifier_id",first);
        return s;
    }
    @Test void validSemanticSubsetHasNoErrors() { assertTrue(codes(base()).isEmpty()); }
    @Test void detectsDuplicateClassifierNamesIgnoringCase() {
        var s=base(); ((ObjectNode)s.path("elements").get(1)).put("name","customer");
        assertTrue(codes(s).contains("DUPLICATE_NAME"));
    }
    @Test void detectsTypeXorMultiplicityAndCrossModelReference() {
        var s=base(); var p=(ObjectNode)s.path("properties").get(0);
        p.put("type_classifier_id",second).put("lower_bound",2);
        assertTrue(codes(s).containsAll(Set.of("INVALID_TYPE","INVALID_MULTIPLICITY")));
        p.put("type_kind","CLASSIFIER").putNull("primitive_type").put("type_classifier_id",UUID.randomUUID().toString());
        assertTrue(codes(s).contains("CROSS_MODEL_OR_MISSING_REFERENCE"));
    }
    @Test void detectsMultipleReturns() {
        var s=base();
        for (int index=0;index<2;index++) {
            String id=UUID.randomUUID().toString(); element(s,id,"PARAMETER","");
            row(s,"parameters").put("element_id",id).put("operation_id",operation).put("direction","RETURN")
                    .put("type_kind","PRIMITIVE").put("primitive_type","BOOLEAN").put("lower_bound",1).put("upper_bound",1).put("position",index);
        }
        assertTrue(codes(s).contains("MULTIPLE_RETURN_PARAMETERS"));
    }
    @Test void detectsIncompleteAssociation() {
        var s=base(); String id=UUID.randomUUID().toString(); element(s,id,"ASSOCIATION",""); row(s,"associations").put("element_id",id);
        assertTrue(codes(s).contains("ASSOCIATION_END_COUNT"));
    }
    @Test void detectsInheritanceCycleAndInvalidRealization() {
        var s=base();
        for (int index=0;index<2;index++) {
            String id=UUID.randomUUID().toString(); element(s,id,"GENERALIZATION","");
            row(s,"generalizations").put("element_id",id).put("specific_classifier_id",index==0?first:second).put("general_classifier_id",index==0?second:first);
        }
        String id=UUID.randomUUID().toString(); element(s,id,"REALIZATION","");
        row(s,"realizations").put("element_id",id).put("client_classifier_id",first).put("supplier_classifier_id",second);
        assertTrue(codes(s).containsAll(Set.of("GENERALIZATION_CYCLE","INVALID_REALIZATION")));
    }
    @Test void acceptsOverloadsButRejectsDuplicateOperationSignatures() {
        var s=base(); String id=UUID.randomUUID().toString(); element(s,id,"OPERATION","register");
        row(s,"operations").put("element_id",id).put("owner_classifier_id",first);
        assertTrue(codes(s).contains("DUPLICATE_OPERATION_SIGNATURE"));
        String p=UUID.randomUUID().toString(); element(s,p,"PARAMETER","email");
        row(s,"parameters").put("element_id",p).put("operation_id",id).put("direction","IN").put("position",0)
                .put("type_kind","PRIMITIVE").put("primitive_type","STRING").put("lower_bound",1).put("upper_bound",1);
        assertFalse(codes(s).contains("DUPLICATE_OPERATION_SIGNATURE"));
    }
    private Set<String> codes(JsonNode s) { return engine.validate(s).stream().filter(i->i.severity().equals("ERROR")).map(i->i.code()).collect(Collectors.toSet()); }
    private void element(ObjectNode s,String id,String kind,String name) { row(s,"elements").put("id",id).put("element_type",kind).put("name",name); }
    private ObjectNode row(ObjectNode s,String table) { return ((ArrayNode)s.path(table)).addObject(); }
}
