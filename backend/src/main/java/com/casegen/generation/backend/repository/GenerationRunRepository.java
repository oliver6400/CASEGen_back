package com.casegen.generation.backend.repository;

import static com.casegen.generation.backend.dto.GenerationDtos.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class GenerationRunRepository {
    public record Source(UUID logicalModelId,UUID modelVersionId,UUID profileId,UUID projectId,
            UUID modelId,String modelName,long modelVersionNumber,String status) {}
    public record StoredArtifact(UUID runId,UUID artifactId,UUID projectId,String runStatus,String storageKey,String fileName,String checksum,long sizeBytes) {}
    private final JdbcTemplate jdbc;
    public GenerationRunRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public Optional<Source> source(UUID logicalId) { return jdbc.query("""
        SELECT l.id,l.model_version_id,l.generation_profile_id,m.project_id,m.id,
               COALESCE(v.semantic_snapshot->'model'->>'name',m.name),v.version_number,l.status
        FROM logical_models l JOIN model_versions v ON v.id=l.model_version_id
        JOIN uml_models m ON m.id=v.model_id WHERE l.id=?
        """,(r,i)->new Source(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getObject(3,UUID.class),r.getObject(4,UUID.class),
                r.getObject(5,UUID.class),r.getString(6),r.getLong(7),r.getString(8)),logicalId).stream().findFirst(); }
    public void create(UUID id,Source s,UUID actor,String version) { jdbc.update("INSERT INTO generation_runs(id,logical_model_id,model_version_id,generation_profile_id,requested_by,generator_version,status) VALUES(?,?,?,?,?,?,'QUEUED')",id,s.logicalModelId(),s.modelVersionId(),s.profileId(),actor,version); }
    public void stage(UUID id,String status,String stage) { jdbc.update("UPDATE generation_runs SET status=?,current_stage=?,started_at=COALESCE(started_at,clock_timestamp()) WHERE id=?",status,stage,id); }
    public void success(UUID id) { jdbc.update("UPDATE generation_runs SET status='SUCCESS',current_stage='COMPLETED',finished_at=clock_timestamp() WHERE id=?",id); }
    public void fail(UUID id,String stage,String error) { jdbc.update("UPDATE generation_runs SET status='FAILED',current_stage=?,error_message=?,started_at=COALESCE(started_at,clock_timestamp()),finished_at=clock_timestamp() WHERE id=?",stage,error,id); }
    public UUID startTest(UUID run,String type) { UUID id=UUID.randomUUID();jdbc.update("INSERT INTO generation_test_runs(id,generation_run_id,test_type,status) VALUES(?,?,?,'RUNNING')",id,run,type);return id; }
    public void finishTest(UUID id,boolean ok,String output) { jdbc.update("UPDATE generation_test_runs SET status=?,output_log=?,finished_at=clock_timestamp() WHERE id=?",ok?"SUCCESS":"FAILED",bounded(output,100_000),id); }
    public void artifact(UUID id,UUID run,String key,String file,String checksum,long size) { jdbc.update("INSERT INTO generation_artifacts(id,generation_run_id,artifact_type,storage_key,file_name,checksum,size_bytes) VALUES(?,?,'BACKEND_ZIP',?,?,?,?)",id,run,key,file,checksum,size); }
    public Optional<UUID> project(UUID run) { return jdbc.query("SELECT m.project_id FROM generation_runs r JOIN model_versions v ON v.id=r.model_version_id JOIN uml_models m ON m.id=v.model_id WHERE r.id=?",(x,i)->x.getObject(1,UUID.class),run).stream().findFirst(); }
    public Optional<StoredArtifact> storedArtifact(UUID runId,UUID artifactId) { return jdbc.query("""
        SELECT r.id,a.id,m.project_id,r.status,a.storage_key,a.file_name,a.checksum,a.size_bytes
        FROM generation_runs r JOIN generation_artifacts a ON a.generation_run_id=r.id
        JOIN model_versions v ON v.id=r.model_version_id JOIN uml_models m ON m.id=v.model_id
        WHERE r.id=? AND a.id=?
        """,(x,i)->new StoredArtifact(x.getObject(1,UUID.class),x.getObject(2,UUID.class),x.getObject(3,UUID.class),x.getString(4),x.getString(5),x.getString(6),x.getString(7),x.getLong(8)),runId,artifactId).stream().findFirst(); }
    public Optional<Response> find(UUID id) { return jdbc.query("""
        SELECT r.*,v.version_number,COALESCE(l.profile_snapshot->'profile'->>'name','GenerationProfile') AS profile_name
        FROM generation_runs r JOIN model_versions v ON v.id=r.model_version_id
        JOIN logical_models l ON l.id=r.logical_model_id WHERE r.id=?
        """,(r,i)->new Response(id,r.getObject("logical_model_id",UUID.class),r.getObject("model_version_id",UUID.class),r.getLong("version_number"),r.getObject("generation_profile_id",UUID.class),r.getString("profile_name"),r.getObject("requested_by",UUID.class),r.getString("generator_version"),r.getString("status"),r.getString("current_stage"),r.getString("error_message"),instant(r,"started_at"),instant(r,"finished_at"),r.getTimestamp("created_at").toInstant(),tests(id),artifacts(id)),id).stream().findFirst(); }
    public List<Response> list(UUID logicalModelId) { return jdbc.query("SELECT id FROM generation_runs WHERE logical_model_id=? ORDER BY created_at DESC,id DESC",(r,i)->r.getObject(1,UUID.class),logicalModelId).stream().map(id->find(id).orElseThrow()).toList(); }
    private List<TestResult> tests(UUID run) { return jdbc.query("SELECT * FROM generation_test_runs WHERE generation_run_id=? ORDER BY started_at,id",(r,i)->new TestResult(r.getObject("id",UUID.class),r.getString("test_type"),r.getString("status"),r.getString("output_log"),r.getTimestamp("started_at").toInstant(),instant(r,"finished_at")),run); }
    private List<Artifact> artifacts(UUID run) { return jdbc.query("SELECT * FROM generation_artifacts WHERE generation_run_id=? ORDER BY created_at,id",(r,i)->new Artifact(r.getObject("id",UUID.class),r.getString("artifact_type"),r.getString("file_name"),r.getString("checksum"),r.getLong("size_bytes"),r.getTimestamp("created_at").toInstant()),run); }
    private static java.time.Instant instant(java.sql.ResultSet r,String name) throws java.sql.SQLException { var t=r.getTimestamp(name);return t==null?null:t.toInstant(); }
    private static String bounded(String value,int limit) { if(value==null)return null;return value.length()<=limit?value:value.substring(value.length()-limit); }
}
