package com.casegen.generation.repository;

import com.casegen.generation.dto.*;
import com.casegen.modeling.service.ModelSnapshotReader;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class GenerationProfileRepository {
    private final JdbcTemplate jdbc;
    private final ModelSnapshotReader json;
    public GenerationProfileRepository(JdbcTemplate jdbc, ModelSnapshotReader json) { this.jdbc = jdbc; this.json = json; }
    public List<GenerationProfileResponse> profiles(UUID modelId) {
        return jdbc.query("SELECT * FROM generation_profiles WHERE model_id=? ORDER BY name,id", (r,i) -> new GenerationProfileResponse(
                r.getObject("id",UUID.class),modelId,r.getString("name"),r.getString("target"),r.getString("group_id"),r.getString("artifact_id"),
                r.getString("base_package"),r.getInt("java_version"),r.getString("spring_boot_version"),r.getString("database"),json.parse(r.getString("config_json"))),modelId);
    }
    public void save(UUID id, UUID modelId, GenerationProfileRequest r, String config, boolean create) {
        if (create) jdbc.update("INSERT INTO generation_profiles(id,model_id,name,target,group_id,artifact_id,base_package,java_version,spring_boot_version,database,config_json) VALUES (?,?,?,?,?,?,?,?,?,?,?::jsonb)",
                id,modelId,r.name().trim(),r.target().name(),r.groupId(),r.artifactId(),r.basePackage(),r.javaVersion(),r.springBootVersion(),r.database().name(),config);
        else jdbc.update("UPDATE generation_profiles SET name=?,target=?,group_id=?,artifact_id=?,base_package=?,java_version=?,spring_boot_version=?,database=?,config_json=?::jsonb WHERE id=? AND model_id=?",
                r.name().trim(),r.target().name(),r.groupId(),r.artifactId(),r.basePackage(),r.javaVersion(),r.springBootVersion(),r.database().name(),config,id,modelId);
    }
    public void delete(UUID id) { jdbc.update("DELETE FROM generation_profiles WHERE id=?",id); }
    public List<ElementMappingResponse> mappings(UUID profileId) {
        return jdbc.query("SELECT * FROM generation_element_mappings WHERE generation_profile_id=? ORDER BY uml_element_id", (r,i) -> new ElementMappingResponse(
                r.getObject("id",UUID.class),profileId,r.getObject("uml_element_id",UUID.class),r.getString("target_kind"),json.parse(r.getString("config_json"))),profileId);
    }
    public void saveMapping(UUID id, UUID profileId, ElementMappingRequest r, String config, boolean create) {
        if (create) jdbc.update("INSERT INTO generation_element_mappings(id,generation_profile_id,uml_element_id,target_kind,config_json) VALUES (?,?,?,?,?::jsonb)",id,profileId,r.umlElementId(),r.targetKind().name(),config);
        else jdbc.update("UPDATE generation_element_mappings SET uml_element_id=?,target_kind=?,config_json=?::jsonb WHERE id=? AND generation_profile_id=?",r.umlElementId(),r.targetKind().name(),config,id,profileId);
    }
    public void deleteMapping(UUID id) { jdbc.update("DELETE FROM generation_element_mappings WHERE id=?",id); }
    public String classifierKind(UUID elementId, UUID modelId) {
        return jdbc.query("SELECT c.kind FROM uml_classifiers c JOIN uml_elements e ON e.id=c.element_id WHERE e.id=? AND e.model_id=?",(r,i)->r.getString(1),elementId,modelId).stream().findFirst().orElse(null);
    }
}
