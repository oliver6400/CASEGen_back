package com.casegen.versioning.repository;

import com.casegen.modeling.service.ModelSnapshotReader;
import com.casegen.versioning.dto.ModelVersionResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ModelVersionRepository {
    private final JdbcTemplate jdbc;
    private final ModelSnapshotReader snapshots;
    public ModelVersionRepository(JdbcTemplate jdbc, ModelSnapshotReader snapshots) { this.jdbc = jdbc; this.snapshots = snapshots; }
    public void append(UUID id, UUID modelId, UUID validationId, UUID actor, String semantic, String diagram) {
        jdbc.update("""
            INSERT INTO model_versions(id,model_id,validation_run_id,version_number,created_by,semantic_snapshot,diagram_snapshot,checksum)
            VALUES (?,?,?,(SELECT COALESCE(MAX(version_number),0)+1 FROM model_versions WHERE model_id=?),?,?::jsonb,?::jsonb,?)
            """, id, modelId, validationId, modelId, actor, semantic, diagram, ModelSnapshotReader.checksum(semantic));
    }
    public List<ModelVersionResponse> list(UUID modelId) {
        return jdbc.query("SELECT * FROM model_versions WHERE model_id=? ORDER BY version_number", (r, i) -> new ModelVersionResponse(
                r.getObject("id", UUID.class), modelId, r.getObject("validation_run_id", UUID.class), r.getLong("version_number"), r.getObject("created_by", UUID.class),
                snapshots.parse(r.getString("semantic_snapshot")), snapshots.parse(r.getString("diagram_snapshot")), r.getString("checksum"), r.getTimestamp("created_at").toInstant()), modelId);
    }
}
