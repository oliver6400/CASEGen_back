package com.casegen.generation.logical.service;

import com.casegen.generation.logical.dto.LogicalModelDtos.*;
import com.casegen.modeling.service.ModelSnapshotReader;
import java.util.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/** Pure transformation. Reads only the supplied immutable semantic/profile snapshots. */
@Component
public class LogicalModelTransformer {
    public Plan transform(JsonNode semantic, JsonNode profileSnapshot) {
        return new Transformation(semantic, profileSnapshot).build();
    }

    private static final class MutableTable {
        final String name, classifier, association;
        final List<Column> columns = new ArrayList<>();
        final List<ForeignKey> foreignKeys = new ArrayList<>();
        final List<Key> unique = new ArrayList<>();
        final List<Index> indexes = new ArrayList<>();
        final List<Check> checks = new ArrayList<>();
        Key primaryKey;
        MutableTable(String name, String classifier, String association) { this.name=name; this.classifier=classifier; this.association=association; }
        Table freeze() { return new Table(name,classifier,association,List.copyOf(columns),primaryKey,List.copyOf(foreignKeys),List.copyOf(unique),List.copyOf(indexes),List.copyOf(checks)); }
    }

    private static final class Transformation {
        final JsonNode semantic, profile;
        final Map<String,JsonNode> elements, classifiers, properties, mappings = new TreeMap<>();
        final Map<String,String> parents = new TreeMap<>();
        final Map<String,MutableTable> entityTables = new TreeMap<>();
        final Map<String,MutableTable> tables = new TreeMap<>();
        final Set<String> endProperties = new HashSet<>(), visiting = new HashSet<>();
        final List<Issue> issues = new ArrayList<>();
        Transformation(JsonNode semantic, JsonNode profile) {
            this.semantic=semantic; this.profile=profile;
            elements=byId(semantic.path("elements"),"id");
            classifiers=byId(semantic.path("classifiers"),"element_id");
            properties=byId(semantic.path("properties"),"element_id");
        }
        Plan build() {
            if (semantic.path("schemaVersion").asInt()!=1) error("UNSUPPORTED_SNAPSHOT","Unsupported semantic snapshot schema",null);
            if (!text(profile.path("profile"),"target").equals("SPRING_BOOT") || !text(profile.path("profile"),"database").equals("POSTGRESQL"))
                error("UNSUPPORTED_TARGET","V9 supports SPRING_BOOT / POSTGRESQL only",null);
            String strategy=profile.path("profile").path("configJson").path("inheritanceStrategy").asText("JOINED");
            if (!strategy.equals("JOINED")) error("UNSUPPORTED_INHERITANCE","Only JOINED inheritance is supported",null);
            for (JsonNode m : profile.path("mappings")) {
                String id=text(m,"umlElementId"), kind=text(m,"targetKind");
                if (mappings.putIfAbsent(id,m)!=null) error("DUPLICATE_MAPPING","Duplicate classifier mapping",id);
                if (!classifiers.containsKey(id)) error("MAPPING_NOT_IN_VERSION","Mapping classifier does not exist in this ModelVersion",id);
                if (!Set.of("ENTITY","JAVA_INTERFACE","IGNORE").contains(kind)) error("UNSUPPORTED_MAPPING","V9 supports ENTITY, JAVA_INTERFACE and IGNORE only",id);
                if (kind.equals("ENTITY") && !text(classifiers.get(id),"kind").equals("CLASS")) error("INVALID_ENTITY_MAPPING","ENTITY requires a UML Class",id);
                if (kind.equals("JAVA_INTERFACE") && !text(classifiers.get(id),"kind").equals("INTERFACE")) error("INVALID_INTERFACE_MAPPING","JAVA_INTERFACE requires a UML Interface",id);
            }
            for (String id : classifiers.keySet()) if (!mappings.containsKey(id)) error("MAPPING_REQUIRED","Configure ENTITY, JAVA_INTERFACE or IGNORE for every classifier in the version",id);
            for (JsonNode e : semantic.path("associationEnds")) endProperties.add(text(e,"property_id"));
            for (JsonNode g : semantic.path("generalizations")) {
                String child=text(g,"specific_classifier_id"), parent=text(g,"general_classifier_id");
                if (kind(child).equals("ENTITY")) {
                    if (!kind(parent).equals("ENTITY")) error("INHERITANCE_PARENT_NOT_ENTITY","JOINED parent must be mapped as ENTITY",text(g,"element_id"));
                    if (parents.putIfAbsent(child,parent)!=null) error("MULTIPLE_INHERITANCE_UNSUPPORTED","JOINED MVP supports one parent per ENTITY",child);
                }
            }
            for (String id : mappings.keySet()) if (kind(id).equals("ENTITY")) entity(id);
            for (String id : mappings.keySet()) if (kind(id).equals("ENTITY")) fields(id);
            for (JsonNode a : semantic.path("associations")) association(a);
            if (entityTables.isEmpty()) error("NO_ENTITIES","Configure at least one ENTITY with an explicit idPropertyId",null);
            // Never persist a partial relational schema as an executable/VALID model.
            boolean invalid=issues.stream().anyMatch(i -> i.severity().equals("ERROR"));
            return new Plan(invalid?List.of():tables.values().stream().map(MutableTable::freeze).toList(),List.copyOf(issues));
        }
        MutableTable entity(String id) {
            if (entityTables.containsKey(id)) return entityTables.get(id);
            if (!visiting.add(id)) { error("GENERALIZATION_CYCLE","Generalization contains a cycle",id); return null; }
            MutableTable parent=parents.containsKey(id)?entity(parents.get(id)):null;
            JsonNode config=config(id);
            String name=identifier(config.path("tableName").asText(elementName(id)),id);
            MutableTable table=new MutableTable(name,id,null);
            if (tables.putIfAbsent(name,table)!=null) error("TABLE_NAME_COLLISION","Two entities/join tables map to table "+name,id);
            entityTables.put(id,table);
            String pkId=config.path("idPropertyId").asText("");
            if (pkId.isEmpty()) error("ENTITY_ID_REQUIRED","ENTITY mapping requires explicit configJson.idPropertyId",id);
            JsonNode pk=properties.get(pkId);
            if (pk==null || !ownsOrInherits(id,text(pk,"owner_classifier_id"),new HashSet<>()) || endProperties.contains(pkId)
                    || !text(pk,"type_kind").equals("PRIMITIVE") || !requiredScalar(pk)
                    || pk.path("is_static").asBoolean() || pk.path("is_derived").asBoolean()
                    || !Set.of("STRING","INTEGER","UNLIMITED_NATURAL").contains(text(pk,"primitive_type"))) {
                error("INVALID_ENTITY_ID","idPropertyId must reference an owned/inherited required scalar String, Integer or UnlimitedNatural property",id);
            } else {
                String column=columnName(id,pkId);
                addColumn(table,column,pkId,sqlType(pk),false);
                table.primaryKey=new Key(auto("pk_"+name),List.of(column),pkId);
                if (text(pk,"primitive_type").equals("UNLIMITED_NATURAL")) naturalCheck(table,column,pkId);
                if (parent!=null && parent.primaryKey!=null) {
                    Column parentPk=pkColumn(parent);
                    if (!parentPk.sqlType().equals(sqlType(pk))) error("JOINED_ID_TYPE_MISMATCH","Child and parent identifiers must have the same SQL type",id);
                    addForeignKey(table,parent,List.of(new ColumnPair(column,parentPk.name())),"CASCADE",id,"joined_"+parent.name);
                }
            }
            visiting.remove(id); return table;
        }
        boolean ownsOrInherits(String classifier,String owner,Set<String> seen) {
            return classifier.equals(owner) || (seen.add(classifier) && parents.containsKey(classifier) && ownsOrInherits(parents.get(classifier),owner,seen));
        }
        void fields(String id) {
            MutableTable table=entityTables.get(id);
            if (table==null || table.primaryKey==null) return;
            String pkId=config(id).path("idPropertyId").asText("");
            for (var entry : properties.entrySet()) {
                String pid=entry.getKey();JsonNode p=entry.getValue();
                if (!text(p,"owner_classifier_id").equals(id) || endProperties.contains(pid) || pid.equals(pkId)) continue;
                if (p.path("is_static").asBoolean() || p.path("is_derived").asBoolean()) {
                    warning("NON_PERSISTENT_PROPERTY","Static/derived property is not persisted",pid); continue;
                }
                if (!scalar(p)) { error("MULTIVALUED_PROPERTY_UNSUPPORTED","MVP properties must have multiplicity 1 or 0..1; use an Association for collections",pid); continue; }
                String column=columnName(id,pid);boolean nullable=p.path("lower_bound").asInt()==0;
                if (text(p,"type_kind").equals("PRIMITIVE")) {
                    String type=sqlType(p);
                    if (type.isEmpty()) { error("UNSUPPORTED_PRIMITIVE","Unsupported primitive type",pid); continue; }
                    addColumn(table,column,pid,type,nullable);
                    if (text(p,"primitive_type").equals("UNLIMITED_NATURAL")) naturalCheck(table,column,pid);
                } else {
                    MutableTable referenced=entityTables.get(text(p,"type_classifier_id"));
                    if (referenced==null || referenced.primaryKey==null) { error("PROPERTY_TYPE_NOT_ENTITY","Classifier-typed property must reference an ENTITY with a valid identifier",pid); continue; }
                    Column key=pkColumn(referenced);
                    addColumn(table,column,pid,key.sqlType(),nullable);
                    addForeignKey(table,referenced,List.of(new ColumnPair(column,key.name())),"NO ACTION",pid,column);
                }
                JsonNode pc=config(id).path("properties").path(pid);
                if (pc.path("unique").asBoolean()) table.unique.add(new Key(auto("uq_"+table.name+"_"+column),List.of(column),pid));
                if (pc.path("index").asBoolean() && table.indexes.stream().noneMatch(i -> i.columns().equals(List.of(column))))
                    table.indexes.add(new Index(auto("ix_"+table.name+"_"+column),List.of(column),false,pid));
            }
        }
        void association(JsonNode association) {
            String aid=text(association,"element_id");
            List<JsonNode> ends=new ArrayList<>();
            for (JsonNode e:semantic.path("associationEnds")) if (text(e,"association_id").equals(aid)) ends.add(e);
            ends.sort(Comparator.comparingInt(e -> e.path("member_order").asInt()));
            if (ends.size()!=2) { error("ASSOCIATION_END_COUNT","Association must have exactly two ends",aid); return; }
            JsonNode ea=ends.get(0),eb=ends.get(1),pa=properties.get(text(ea,"property_id")),pb=properties.get(text(eb,"property_id"));
            if (pa==null || pb==null) { error("INVALID_ASSOCIATION_END","Association end property is missing in the version",aid);return; }
            String ca=text(pa,"type_classifier_id"),cb=text(pb,"type_classifier_id");
            if (kind(ca).equals("IGNORE") || kind(cb).equals("IGNORE")) { warning("IGNORED_ASSOCIATION","Association involving IGNORE is not persisted",aid);return; }
            MutableTable a=entityTables.get(ca),b=entityTables.get(cb);
            if (a==null || b==null || a.primaryKey==null || b.primaryKey==null) { error("ASSOCIATION_ENDPOINT_NOT_ENTITY","Association endpoints must be ENTITY mappings with identifiers",aid);return; }
            if (!validMultiplicity(pa) || !validMultiplicity(pb)) { error("INVALID_MULTIPLICITY","Association end multiplicity is invalid",aid);return; }
            if ((!pa.path("upper_bound").isNull() && pa.path("upper_bound").asInt()==0)
                    || (!pb.path("upper_bound").isNull() && pb.path("upper_bound").asInt()==0)) {
                error("ZERO_UPPER_BOUND_UNSUPPORTED","Association with upper=0 cannot be represented by an ordinary FK in MVP",aid);return;
            }
            if ((!pa.path("upper_bound").isNull() && pa.path("upper_bound").asInt()>1)
                    || (!pb.path("upper_bound").isNull() && pb.path("upper_bound").asInt()>1))
                warning("COLLECTION_MAXIMUM_NOT_ENFORCED","Finite collection maximum requires application-level validation",aid);
            JsonNode cfg=profile.path("profile").path("configJson").path("associations").path(aid);
            boolean manyA=many(pa),manyB=many(pb);
            boolean wholeA=text(ea,"aggregation").equals("COMPOSITE"),wholeB=text(eb,"aggregation").equals("COMPOSITE");
            if (wholeA || wholeB) {
                if ((wholeA&&wholeB) || (wholeA&&manyA) || (wholeB&&manyB)) { error("INVALID_COMPOSITION","Composite whole must have upper <= 1 and only one whole end",aid);return; }
                if (!text(cfg,"fkOwnerClassifierId").isEmpty() && !text(cfg,"fkOwnerClassifierId").equals(wholeA?cb:ca)) { error("COMPOSITION_OWNER_CONFLICT","Composition FK must belong to the part table",aid);return; }
                reference(wholeA?b:a,wholeA?a:b,wholeA?pa:pb,aid,cfg,!manyA&&!manyB,"CASCADE",wholeA?1:2);return;
            }
            if (manyA && manyB) { joinTable(a,b,pa,pb,aid,cfg);return; }
            if (manyA || manyB) { reference(manyA?a:b,manyA?b:a,manyA?pb:pa,aid,cfg,false,"NO ACTION",manyA?2:1);return; }
            String owner=text(cfg,"fkOwnerClassifierId");
            if (owner.isEmpty()) {
                String oa=text(ea,"ownership").equals("CLASSIFIER")?text(pa,"owner_classifier_id"):"";
                String ob=text(eb,"ownership").equals("CLASSIFIER")?text(pb,"owner_classifier_id"):"";
                if (!oa.isEmpty() && ob.isEmpty()) owner=oa; else if (!ob.isEmpty() && oa.isEmpty()) owner=ob;
            }
            if (owner.isEmpty() || (!owner.equals(ca)&&!owner.equals(cb)) || (ca.equals(cb) && cfg.path("referencedEndOrder").asInt()!=1 && cfg.path("referencedEndOrder").asInt()!=2)) {
                error("AMBIGUOUS_ONE_TO_ONE","Configure associations[associationId].fkOwnerClassifierId; self 1:1 also needs referencedEndOrder (1 or 2)",aid);return;
            }
            boolean referencesA=ca.equals(cb)?cfg.path("referencedEndOrder").asInt()==1:owner.equals(cb);
            reference(referencesA?b:a,referencesA?a:b,referencesA?pa:pb,aid,cfg,true,"NO ACTION",referencesA?1:2);
        }
        void reference(MutableTable owner,MutableTable target,JsonNode targetEnd,String aid,JsonNode cfg,boolean unique,String delete,int order) {
            String role=elementName(text(targetEnd,"element_id"));
            String prefix=role.isBlank()?target.name:role;
            String column=identifier(cfg.path("fkColumnName").asText(prefix+"_"+pkColumn(target).name()),aid);
            addColumn(owner,column,text(targetEnd,"element_id"),pkColumn(target).sqlType(),targetEnd.path("lower_bound").asInt()==0);
            addForeignKey(owner,target,List.of(new ColumnPair(column,pkColumn(target).name())),delete,aid,column+"_"+order);
            if (unique) owner.unique.add(new Key(auto("uq_"+owner.name+"_"+column),List.of(column),aid));
        }
        void joinTable(MutableTable a,MutableTable b,JsonNode pa,JsonNode pb,String aid,JsonNode cfg) {
            String defaultName=elementName(aid);if (defaultName.isBlank()) defaultName=a.name+"_"+b.name;
            String name=identifier(cfg.path("joinTableName").asText(defaultName),aid);
            MutableTable join=new MutableTable(name,null,aid);
            if (tables.putIfAbsent(name,join)!=null) { error("TABLE_NAME_COLLISION","Join table name collides: "+name,aid);return; }
            List<String> keys=new ArrayList<>();
            for (int i=0;i<2;i++) {
                MutableTable target=i==0?a:b;JsonNode end=i==0?pa:pb;
                String role=elementName(text(end,"element_id"));String prefix=role.isBlank()?target.name:role;
                if (a==b && role.isBlank()) prefix=(i==0?"a_":"b_")+prefix;
                String column=identifier(cfg.path(i==0?"joinColumnA":"joinColumnB").asText(prefix+"_"+pkColumn(target).name()),aid);
                addColumn(join,column,text(end,"element_id"),pkColumn(target).sqlType(),false);keys.add(column);
                addForeignKey(join,target,List.of(new ColumnPair(column,pkColumn(target).name())),"NO ACTION",aid,column);
            }
            join.primaryKey=new Key(auto("pk_"+name),List.copyOf(keys),aid);
            if (pa.path("lower_bound").asInt()>0 || pb.path("lower_bound").asInt()>0)
                warning("COLLECTION_MINIMUM_NOT_ENFORCED","Join table does not enforce minimum collection size; validate it at application level",aid);
        }
        void addForeignKey(MutableTable owner,MutableTable target,List<ColumnPair> pairs,String delete,String source,String suffix) {
            String name=auto("fk_"+owner.name+"_"+suffix);
            if (owner.foreignKeys.stream().anyMatch(f -> f.name().equals(name))) { error("FK_NAME_COLLISION","Foreign key name collides: "+name,source); return; }
            owner.foreignKeys.add(new ForeignKey(name,target.name,List.copyOf(pairs),delete,source));
            owner.indexes.add(new Index(auto("ix_"+owner.name+"_"+suffix),pairs.stream().map(ColumnPair::column).toList(),false,source));
        }
        void addColumn(MutableTable t,String name,String source,String type,boolean nullable) {
            if (t.columns.stream().anyMatch(c -> c.name().equals(name))) { error("COLUMN_NAME_COLLISION","Column already exists: "+t.name+"."+name,source);return; }
            t.columns.add(new Column(name,source,type,nullable,t.columns.size()+1));
        }
        void naturalCheck(MutableTable t,String name,String source) { t.checks.add(new Check(auto("ck_"+t.name+"_"+name+"_nonnegative"),name+" >= 0",source)); }
        Column pkColumn(MutableTable t) { return t.columns.stream().filter(c -> t.primaryKey.columns().contains(c.name())).findFirst().orElseThrow(); }
        String columnName(String classifier,String property) { return identifier(config(classifier).path("properties").path(property).path("columnName").asText(elementName(property)),property); }
        JsonNode config(String id) { return mappings.containsKey(id)?mappings.get(id).path("configJson"):profile.path("missing"); }
        String kind(String id) { return mappings.containsKey(id)?text(mappings.get(id),"targetKind"):""; }
        String elementName(String id) { return text(elements.get(id),"name"); }
        String identifier(String input,String source) {
            String result=snake(input);
            if (!result.matches("[a-z_][a-z0-9_]*") || result.length()>63) error("INVALID_SQL_NAME","SQL name must be a valid identifier of at most 63 ASCII characters: "+input,source);
            return result.isEmpty()?"invalid_name":result;
        }
        void error(String code,String message,String source) { issues.add(new Issue("ERROR",code,message,source,"/logical")); }
        void warning(String code,String message,String source) { issues.add(new Issue("WARNING",code,message,source,"/logical")); }
    }
    private static Map<String,JsonNode> byId(JsonNode list,String key) { Map<String,JsonNode> map=new TreeMap<>();for (JsonNode n:list) map.put(text(n,key),n);return map; }
    private static String text(JsonNode n,String key) { return n==null?"":n.path(key).asText(""); }
    private static boolean validMultiplicity(JsonNode p) { return p.path("lower_bound").asInt(-1)>=0 && (p.path("upper_bound").isNull() || p.path("upper_bound").asInt(-1)>=p.path("lower_bound").asInt()); }
    private static boolean scalar(JsonNode p) { return validMultiplicity(p) && !p.path("upper_bound").isNull() && p.path("upper_bound").asInt()==1 && p.path("lower_bound").asInt()<=1; }
    private static boolean requiredScalar(JsonNode p) { return scalar(p) && p.path("lower_bound").asInt()==1; }
    private static boolean many(JsonNode p) { return p.path("upper_bound").isNull() || p.path("upper_bound").asInt()>1; }
    private static String sqlType(JsonNode p) { return switch(text(p,"primitive_type")) { case "STRING" -> "VARCHAR(255)";case "INTEGER" -> "INTEGER";case "BOOLEAN" -> "BOOLEAN";case "REAL" -> "NUMERIC(19,2)";case "UNLIMITED_NATURAL" -> "BIGINT";default -> ""; }; }
    private static String snake(String value) { return value.replaceAll("([A-Z]+)([A-Z][a-z])","$1_$2").replaceAll("([a-z0-9])([A-Z])","$1_$2").toLowerCase(Locale.ROOT); }
    private static String auto(String value) { String name=snake(value);return name.length()<=63?name:name.substring(0,50)+"_"+ModelSnapshotReader.checksum(name).substring(0,12); }
}
