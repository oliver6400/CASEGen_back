package com.casegen.generation.logical.repository;

import com.casegen.generation.logical.dto.LogicalModelDtos.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class LogicalModelRepository {
    public record Source(UUID modelId, UUID projectId, JsonNode semantic) {}
    private final JdbcTemplate jdbc;
    private final JsonMapper json=JsonMapper.builder().build();
    public LogicalModelRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public Optional<Source> source(UUID versionId) {
        return jdbc.query("SELECT v.model_id,m.project_id,v.semantic_snapshot FROM model_versions v JOIN uml_models m ON m.id=v.model_id WHERE v.id=?",
                (r,i)->new Source(r.getObject(1,UUID.class),r.getObject(2,UUID.class),json.readTree(r.getString(3))),versionId).stream().findFirst();
    }
    public Optional<UUID> projectId(UUID modelId) {
        return jdbc.query("SELECT project_id FROM uml_models WHERE id=?",(r,i)->r.getObject(1,UUID.class),modelId).stream().findFirst();
    }
    public List<Summary> list(UUID modelId) {
        return jdbc.query("""
            SELECT l.id,l.model_version_id,v.version_number,l.generation_profile_id,
                   l.profile_snapshot->'profile'->>'name' AS profile_name,l.status,l.checksum,l.created_at
            FROM logical_models l JOIN model_versions v ON v.id=l.model_version_id
            WHERE v.model_id=? ORDER BY l.created_at DESC,l.id
            """,(r,i)->new Summary(r.getObject("id",UUID.class),r.getObject("model_version_id",UUID.class),r.getLong("version_number"),
                r.getObject("generation_profile_id",UUID.class),r.getString("profile_name"),r.getString("status"),r.getString("checksum"),r.getTimestamp("created_at").toInstant()),modelId);
    }
    /** Same lock used by existing profile/mapping mutations; no semantic fields read. */
    public void lockProfileBoundary(UUID modelId) { jdbc.queryForObject("SELECT id FROM uml_models WHERE id=? FOR UPDATE",UUID.class,modelId); }
    public void append(UUID id, UUID versionId, UUID profileId, JsonNode profile, Plan plan, String checksum, UUID actor) {
        jdbc.update("INSERT INTO logical_models(id,model_version_id,generation_profile_id,profile_snapshot,status,checksum,issues_json,created_by) VALUES(?,?,?,?::jsonb,?,?,?::jsonb,?)",
                id,versionId,profileId,json.writeValueAsString(profile),plan.valid()?"VALID":"INVALID",checksum,json.writeValueAsString(plan.issues()),actor);
        Map<String,UUID> tableIds=new HashMap<>();Map<String,Map<String,UUID>> columnIds=new HashMap<>();
        for (Table t:plan.tables()) {
            UUID tid=UUID.randomUUID();tableIds.put(t.name(),tid);columnIds.put(t.name(),new HashMap<>());
            jdbc.update("INSERT INTO logical_tables(id,logical_model_id,source_classifier_id,source_association_id,name) VALUES(?,?,?,?,?)",tid,id,uuid(t.sourceClassifierId()),uuid(t.sourceAssociationId()),t.name());
            for (Column c:t.columns()) {
                UUID cid=UUID.randomUUID();columnIds.get(t.name()).put(c.name(),cid);
                jdbc.update("INSERT INTO logical_columns(id,table_id,source_property_id,name,sql_type,nullable,ordinal_position) VALUES(?,?,?,?,?,?,?)",cid,tid,uuid(c.sourcePropertyId()),c.name(),c.sqlType(),c.nullable(),c.ordinalPosition());
            }
        }
        for (Table t:plan.tables()) {
            UUID tid=tableIds.get(t.name());Map<String,UUID> cols=columnIds.get(t.name());
            Key pk=t.primaryKey();UUID pkid=UUID.randomUUID();
            jdbc.update("INSERT INTO logical_primary_keys(id,table_id,name,source_element_id) VALUES(?,?,?,?)",pkid,tid,pk.name(),uuid(pk.sourceElementId()));
            ordered("logical_primary_key_columns","primary_key_id",pkid,tid,pk.columns(),cols);
            for (ForeignKey fk:t.foreignKeys()) {
                UUID fid=UUID.randomUUID(),ref=tableIds.get(fk.referencedTable());
                jdbc.update("INSERT INTO logical_foreign_keys(id,table_id,referenced_table_id,name,on_delete,source_element_id) VALUES(?,?,?,?,?,?)",fid,tid,ref,fk.name(),fk.onDelete(),uuid(fk.sourceElementId()));
                int position=0;
                for (ColumnPair pair:fk.columns()) jdbc.update("INSERT INTO logical_foreign_key_columns(foreign_key_id,table_id,referenced_table_id,column_id,referenced_column_id,position) VALUES(?,?,?,?,?,?)",
                        fid,tid,ref,cols.get(pair.column()),columnIds.get(fk.referencedTable()).get(pair.referencedColumn()),++position);
            }
            for (Key uk:t.uniqueConstraints()) {
                UUID uid=UUID.randomUUID();jdbc.update("INSERT INTO logical_unique_constraints(id,table_id,name,source_element_id) VALUES(?,?,?,?)",uid,tid,uk.name(),uuid(uk.sourceElementId()));
                ordered("logical_unique_constraint_columns","unique_constraint_id",uid,tid,uk.columns(),cols);
            }
            for (Index index:t.indexes()) {
                UUID iid=UUID.randomUUID();jdbc.update("INSERT INTO logical_indexes(id,table_id,name,is_unique,source_element_id) VALUES(?,?,?,?,?)",iid,tid,index.name(),index.unique(),uuid(index.sourceElementId()));
                ordered("logical_index_columns","index_id",iid,tid,index.columns(),cols);
            }
            for (Check check:t.checks()) jdbc.update("INSERT INTO logical_check_constraints(id,table_id,name,expression,source_element_id) VALUES(?,?,?,?,?)",UUID.randomUUID(),tid,check.name(),check.expression(),uuid(check.sourceElementId()));
        }
    }
    private void ordered(String table,String key,UUID id,UUID tid,List<String> names,Map<String,UUID> columns) {
        int position=0;for (String name:names) jdbc.update("INSERT INTO "+table+"("+key+",table_id,column_id,position) VALUES(?,?,?,?)",id,tid,columns.get(name),++position);
    }
    public Optional<Response> find(UUID id) {
        return jdbc.query("SELECT * FROM logical_models WHERE id=?",(r,i)->new Response(id,r.getObject("model_version_id",UUID.class),r.getObject("generation_profile_id",UUID.class),
                json.readTree(r.getString("profile_snapshot")),r.getString("status"),r.getString("checksum"),r.getObject("created_by",UUID.class),r.getTimestamp("created_at").toInstant(),List.of(),
                Arrays.asList(json.readValue(r.getString("issues_json"),Issue[].class))),id).stream().findFirst();
    }
    public List<Table> tables(UUID modelId) {
        return jdbc.query("SELECT * FROM logical_tables WHERE logical_model_id=? ORDER BY name",(r,i)-> {
            UUID tid=r.getObject("id",UUID.class);
            List<Column> columns=jdbc.query("SELECT * FROM logical_columns WHERE table_id=? ORDER BY ordinal_position",(c,n)->new Column(c.getString("name"),c.getString("source_property_id"),c.getString("sql_type"),c.getBoolean("nullable"),c.getInt("ordinal_position")),tid);
            List<Key> primary=keys("logical_primary_keys","logical_primary_key_columns","primary_key_id",tid);
            List<ForeignKey> foreign=jdbc.query("SELECT f.*,t.name AS target_name FROM logical_foreign_keys f JOIN logical_tables t ON t.id=f.referenced_table_id WHERE f.table_id=? ORDER BY f.name",(f,n)->new ForeignKey(
                    f.getString("name"),f.getString("target_name"),jdbc.query("SELECT c.name,r.name FROM logical_foreign_key_columns p JOIN logical_columns c ON c.id=p.column_id JOIN logical_columns r ON r.id=p.referenced_column_id WHERE p.foreign_key_id=? ORDER BY p.position",(p,j)->new ColumnPair(p.getString(1),p.getString(2)),f.getObject("id",UUID.class)),f.getString("on_delete"),f.getString("source_element_id")),tid);
            List<Index> indexes=jdbc.query("SELECT * FROM logical_indexes WHERE table_id=? ORDER BY name",(x,n)->new Index(x.getString("name"),names("logical_index_columns","index_id",x.getObject("id",UUID.class)),x.getBoolean("is_unique"),x.getString("source_element_id")),tid);
            List<Check> checks=jdbc.query("SELECT * FROM logical_check_constraints WHERE table_id=? ORDER BY name",(c,n)->new Check(c.getString("name"),c.getString("expression"),c.getString("source_element_id")),tid);
            return new Table(r.getString("name"),r.getString("source_classifier_id"),r.getString("source_association_id"),columns,primary.isEmpty()?null:primary.getFirst(),foreign,
                    keys("logical_unique_constraints","logical_unique_constraint_columns","unique_constraint_id",tid),indexes,checks);
        },modelId);
    }
    private List<Key> keys(String table,String pairs,String field,UUID tid) {
        return jdbc.query("SELECT * FROM "+table+" WHERE table_id=? ORDER BY name",(r,i)->new Key(r.getString("name"),names(pairs,field,r.getObject("id",UUID.class)),r.getString("source_element_id")),tid);
    }
    private List<String> names(String table,String field,UUID id) {
        return jdbc.query("SELECT c.name FROM "+table+" p JOIN logical_columns c ON c.id=p.column_id WHERE p."+field+"=? ORDER BY p.position",(r,i)->r.getString(1),id);
    }
    private UUID uuid(String value) { return value==null||value.isBlank()?null:UUID.fromString(value); }
}
