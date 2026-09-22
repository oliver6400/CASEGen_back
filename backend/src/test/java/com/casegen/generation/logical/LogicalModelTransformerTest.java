package com.casegen.generation.logical;

import static org.junit.jupiter.api.Assertions.*;
import com.casegen.generation.logical.dto.LogicalModelDtos.*;
import com.casegen.generation.logical.service.LogicalModelTransformer;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class LogicalModelTransformerTest {
    private final LogicalModelTransformer transformer=new LogicalModelTransformer();
    private final JsonMapper json=JsonMapper.builder().build();
    @Test void entityPropertiesPrimaryKeyNullabilityAndTypes() {
        Fixture f=new Fixture();String customer=f.entity("Customer");
        f.property(customer,"email","STRING",0,1);f.property(customer,"age","INTEGER",1,1);
        f.property(customer,"active","BOOLEAN",1,1);f.property(customer,"balance","REAL",0,1);f.property(customer,"count","UNLIMITED_NATURAL",1,1);
        Plan plan=run(f);assertTrue(plan.valid(),plan.issues().toString());Table t=plan.tables().getFirst();
        assertEquals("customer",t.name());assertEquals(customer,t.sourceClassifierId());assertEquals(List.of("id"),t.primaryKey().columns());
        assertFalse(column(t,"id").nullable());assertTrue(column(t,"email").nullable());assertFalse(column(t,"age").nullable());
        assertEquals("VARCHAR(255)",column(t,"email").sqlType());assertEquals("INTEGER",column(t,"age").sqlType());
        assertEquals("BOOLEAN",column(t,"active").sqlType());assertEquals("NUMERIC(19,2)",column(t,"balance").sqlType());assertEquals("BIGINT",column(t,"count").sqlType());
        assertTrue(t.checks().stream().anyMatch(c->c.expression().equals("count >= 0")));
        for(int i=0;i<t.columns().size();i++) assertEquals(i+1,t.columns().get(i).ordinalPosition());
    }
    @Test void oneToManyForeignKeyLivesOnManySide() {
        Fixture f=new Fixture();String a=f.entity("Customer"),b=f.entity("Sale");f.association(a,b,1,1,0,null,"NONE");
        Plan p=run(f);assertTrue(p.valid(),p.issues().toString());Table sale=table(p,"sale");
        assertEquals("customer",sale.foreignKeys().getFirst().referencedTable());assertFalse(column(sale,"customer_id").nullable());assertEquals("NO ACTION",sale.foreignKeys().getFirst().onDelete());
        assertTrue(table(p,"customer").foreignKeys().isEmpty());assertEquals(1,sale.indexes().size());
    }
    @Test void reversedOneToManyAndOptionalTarget() {
        Fixture f=new Fixture();String a=f.entity("Sale"),b=f.entity("Customer");f.association(a,b,0,null,0,1,"NONE");
        Plan p=run(f);assertTrue(p.valid());assertTrue(column(table(p,"sale"),"customer_id").nullable());
    }
    @Test void oneToOneRequiresExplicitOwnerAndAddsUnique() {
        Fixture f=new Fixture();String a=f.entity("Customer"),b=f.entity("Address");String aid=f.association(a,b,1,1,0,1,"NONE");
        Plan invalid=run(f);assertFalse(invalid.valid());assertTrue(invalid.tables().isEmpty());assertIssue(invalid,"AMBIGUOUS_ONE_TO_ONE");
        f.associationConfig(aid,Map.of("fkOwnerClassifierId",a));Plan p=run(f);assertTrue(p.valid());
        Table customer=table(p,"customer");assertEquals("address",customer.foreignKeys().getFirst().referencedTable());assertTrue(column(customer,"address_id").nullable());assertEquals(List.of("address_id"),customer.uniqueConstraints().getFirst().columns());
    }
    @Test void manyToManyCreatesJoinTableWithOrderedCompositePrimaryKey() {
        Fixture f=new Fixture();String a=f.entity("Student"),b=f.entity("Course");String aid=f.association(a,b,0,null,0,null,"NONE");
        f.associationConfig(aid,Map.of("joinTableName","Enrollment"));Plan p=run(f);assertTrue(p.valid(),p.issues().toString());
        Table join=table(p,"enrollment");assertNull(join.sourceClassifierId());assertEquals(aid,join.sourceAssociationId());assertEquals(2,join.foreignKeys().size());
        assertEquals(List.of("student_id","course_id"),join.primaryKey().columns());assertTrue(join.columns().stream().noneMatch(Column::nullable));
    }
    @Test void compositionReferencesWholeWithCascade() {
        Fixture f=new Fixture();String whole=f.entity("Order"),part=f.entity("OrderItem");f.association(whole,part,1,1,1,null,"COMPOSITE");
        Plan p=run(f);assertTrue(p.valid());assertEquals("CASCADE",table(p,"order_item").foreignKeys().getFirst().onDelete());assertEquals("order",table(p,"order_item").foreignKeys().getFirst().referencedTable());
    }
    @Test void joinedInheritanceChildPrimaryKeyIsForeignKeyAndInterfaceHasNoTable() {
        Fixture f=new Fixture();String parent=f.entity("Person"),child=f.entity("Customer");f.generalization(child,parent);f.interfaceClassifier("Identifiable");
        Plan p=run(f);assertTrue(p.valid(),p.issues().toString());assertEquals(2,p.tables().size());Table t=table(p,"customer");
        assertEquals("person",t.foreignKeys().getFirst().referencedTable());assertEquals(t.primaryKey().columns().getFirst(),t.foreignKeys().getFirst().columns().getFirst().column());
    }
    @Test void inheritedIdentifierIsAllowedInJoined() {
        Fixture f=new Fixture();String parent=f.entity("Person"),child=f.classifier("Customer","CLASS","ENTITY");f.generalization(child,parent);
        f.mappingConfig(child,Map.of("idPropertyId",f.idProperty(parent)));Plan p=run(f);assertTrue(p.valid(),p.issues().toString());assertEquals(1,table(p,"customer").foreignKeys().size());
    }
    @Test void primitiveMultivaluedIsBlockedWithoutPartialSchema() {
        Fixture f=new Fixture();String id=f.entity("Customer");f.property(id,"tags","STRING",0,null);
        Plan p=run(f);assertFalse(p.valid());assertTrue(p.tables().isEmpty());assertIssue(p,"MULTIVALUED_PROPERTY_UNSUPPORTED");
    }
    @Test void missingIdAndUnsupportedMappingsAreClearIssues() {
        Fixture f=new Fixture();String id=f.entity("Customer");f.mappingConfig(id,Map.of());assertIssue(run(f),"ENTITY_ID_REQUIRED");
        f.mapping(id).put("targetKind","VALUE_OBJECT");assertIssue(run(f),"UNSUPPORTED_MAPPING");
    }
    @Test void collisionsAndMissingMappingsCannotBecomeValid() {
        Fixture f=new Fixture();f.entity("Customer");f.entity("customer");assertIssue(run(f),"TABLE_NAME_COLLISION");
        f.mappingRows.clear();assertIssue(run(f),"MAPPING_REQUIRED");
    }
    @Test void selfManyToManyHasDistinctColumns() {
        Fixture f=new Fixture();String a=f.entity("Person");f.association(a,a,0,null,0,null,"NONE");Plan p=run(f);assertTrue(p.valid(),p.issues().toString());
        Table join=table(p,"person_person");assertEquals(List.of("a_person_id","b_person_id"),join.primaryKey().columns());
    }
    @Test void classifierPropertyCreatesTypedForeignKeyAndUniqueIndexConfiguration() {
        Fixture f=new Fixture();String a=f.entity("Sale"),b=f.entity("Customer");String pid=f.property(a,"customer",null,0,1);
        f.propertyRow(pid).put("type_kind","CLASSIFIER");f.propertyRow(pid).put("type_classifier_id",b);
        Map<String,Object> config=new HashMap<>();config.put("idPropertyId",f.idProperty(a));config.put("properties",Map.of(pid,Map.of("unique",true,"index",true)));f.mappingConfig(a,config);
        Plan p=run(f);assertTrue(p.valid(),p.issues().toString());assertEquals("customer",table(p,"sale").foreignKeys().getFirst().referencedTable());assertEquals(1,table(p,"sale").uniqueConstraints().size());assertEquals(1,table(p,"sale").indexes().size());
    }
    @Test void cycleAndMultipleJoinedParentsAreRejected() {
        Fixture f=new Fixture();String a=f.entity("A"),b=f.entity("B"),c=f.entity("C");f.generalization(a,b);f.generalization(b,a);assertIssue(run(f),"GENERALIZATION_CYCLE");
        f.generalization(a,c);assertIssue(run(f),"MULTIPLE_INHERITANCE_UNSUPPORTED");
    }
    @Test void sameSnapshotsAlwaysProduceSamePlanWithoutMutatingInput() {
        Fixture f=new Fixture();f.entity("Customer");JsonNode semantic=f.semantic(json),profile=f.profile(json);String before=semantic.toString()+profile;
        assertEquals(transformer.transform(semantic,profile),transformer.transform(semantic,profile));assertEquals(before,semantic.toString()+profile);
    }
    private Plan run(Fixture f) { return transformer.transform(f.semantic(json),f.profile(json)); }
    private Table table(Plan p,String name) { return p.tables().stream().filter(t->t.name().equals(name)).findFirst().orElseThrow(); }
    private Column column(Table t,String name) { return t.columns().stream().filter(c->c.name().equals(name)).findFirst().orElseThrow(); }
    private void assertIssue(Plan p,String code) { assertTrue(p.issues().stream().anyMatch(i->i.code().equals(code)),p.issues().toString()); }

    static final class Fixture {
        final List<Map<String,Object>> elements=new ArrayList<>(),classifiers=new ArrayList<>(),properties=new ArrayList<>(),mappingRows=new ArrayList<>(),associations=new ArrayList<>(),ends=new ArrayList<>(),generalizations=new ArrayList<>();
        final Map<String,Object> associationConfigs=new TreeMap<>();
        String entity(String name) { String id=classifier(name,"CLASS","ENTITY");String pk=property(id,"id","STRING",1,1);mappingConfig(id,Map.of("idPropertyId",pk));return id; }
        String interfaceClassifier(String name) { return classifier(name,"INTERFACE","JAVA_INTERFACE"); }
        String classifier(String name,String kind,String target) {
            String id=UUID.randomUUID().toString();elements.add(new HashMap<>(Map.of("id",id,"name",name)));classifiers.add(new HashMap<>(Map.of("element_id",id,"kind",kind)));
            mappingRows.add(new HashMap<>(Map.of("umlElementId",id,"targetKind",target,"configJson",Map.of())));return id;
        }
        String property(String owner,String name,String primitive,int lower,Integer upper) {
            String id=UUID.randomUUID().toString();elements.add(new HashMap<>(Map.of("id",id,"name",name)));Map<String,Object> p=new HashMap<>();
            p.put("element_id",id);p.put("owner_classifier_id",owner);p.put("type_kind","PRIMITIVE");p.put("primitive_type",primitive);p.put("type_classifier_id",null);p.put("lower_bound",lower);p.put("upper_bound",upper);properties.add(p);return id;
        }
        String association(String a,String b,int la,Integer ua,int lb,Integer ub,String aggregation) {
            String id=UUID.randomUUID().toString();elements.add(new HashMap<>(Map.of("id",id,"name","")));associations.add(new HashMap<>(Map.of("element_id",id)));
            for(int i=0;i<2;i++) { String pid=property(null,"",null,i==0?la:lb,i==0?ua:ub);Map<String,Object> p=propertyRow(pid);p.put("type_kind","CLASSIFIER");p.put("type_classifier_id",i==0?a:b);
                ends.add(new HashMap<>(Map.of("property_id",pid,"association_id",id,"member_order",i+1,"aggregation",i==0?aggregation:"NONE","ownership","ASSOCIATION"))); }
            return id;
        }
        void generalization(String child,String parent) { generalizations.add(new HashMap<>(Map.of("element_id",UUID.randomUUID().toString(),"specific_classifier_id",child,"general_classifier_id",parent))); }
        String idProperty(String classifier) { return properties.stream().filter(p->classifier.equals(p.get("owner_classifier_id"))).findFirst().orElseThrow().get("element_id").toString(); }
        Map<String,Object> propertyRow(String id) { return properties.stream().filter(p->id.equals(p.get("element_id"))).findFirst().orElseThrow(); }
        Map<String,Object> mapping(String id) { return mappingRows.stream().filter(p->id.equals(p.get("umlElementId"))).findFirst().orElseThrow(); }
        void mappingConfig(String id,Map<String,Object> config) { mapping(id).put("configJson",config); }
        void associationConfig(String id,Map<String,Object> config) { associationConfigs.put(id,config); }
        JsonNode semantic(JsonMapper json) { return json.valueToTree(Map.of("schemaVersion",1,"elements",elements,"classifiers",classifiers,"properties",properties,"associations",associations,"associationEnds",ends,"generalizations",generalizations)); }
        JsonNode profile(JsonMapper json) { return json.valueToTree(Map.of("profile",Map.of("target","SPRING_BOOT","database","POSTGRESQL","configJson",Map.of("associations",associationConfigs)),"mappings",mappingRows)); }
    }
}
