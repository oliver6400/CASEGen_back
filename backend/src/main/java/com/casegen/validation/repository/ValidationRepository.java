package com.casegen.validation.repository;

import com.casegen.validation.dto.ValidationIssueResponse;
import com.casegen.validation.dto.ValidationResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ValidationRepository {
    private final JdbcTemplate jdbc;
    public ValidationRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public void start(UUID id, UUID modelId, long revision, String generationChecksum, UUID actor) {
        jdbc.update("INSERT INTO validation_runs(id,model_id,model_revision,generation_checksum,status,created_by) VALUES (?,?,?,?,'RUNNING',?)", id, modelId, revision, generationChecksum, actor);
    }
    public void finish(UUID id, List<ValidationIssueResponse> issues) {
        for (var i : issues) jdbc.update("INSERT INTO validation_issues(id,validation_run_id,uml_element_id,severity,code,message,path) VALUES (?,?,?,?,?,?,?)", i.id(), id, i.umlElementId(), i.severity(), i.code(), i.message(), i.path());
        jdbc.update("UPDATE validation_runs SET status=?,finished_at=clock_timestamp() WHERE id=?", issues.stream().anyMatch(i -> i.severity().equals("ERROR")) ? "INVALID" : "VALID", id);
    }
    public Optional<ValidationResponse> find(UUID id) {
        return jdbc.query("SELECT * FROM validation_runs WHERE id=?", this::map, id).stream().findFirst();
    }
    public Optional<ValidationResponse> latest(UUID modelId) {
        return jdbc.query("SELECT * FROM validation_runs WHERE model_id=? ORDER BY run_order DESC LIMIT 1", this::map, modelId).stream().findFirst();
    }
    public String generationChecksum(UUID id) { return jdbc.queryForObject("SELECT generation_checksum FROM validation_runs WHERE id=?", String.class, id); }
    private ValidationResponse map(ResultSet r, int index) throws SQLException {
        UUID id = r.getObject("id", UUID.class);
        var issues = jdbc.query("SELECT * FROM validation_issues WHERE validation_run_id=? ORDER BY path,code,id", (row, n) -> new ValidationIssueResponse(
                row.getObject("id", UUID.class), row.getObject("uml_element_id", UUID.class), row.getString("severity"), row.getString("code"), row.getString("message"), row.getString("path")), id);
        var finished = r.getTimestamp("finished_at");
        return new ValidationResponse(id, r.getObject("model_id", UUID.class), r.getLong("model_revision"), r.getString("status"), false, r.getTimestamp("started_at").toInstant(), finished == null ? null : finished.toInstant(), issues);
    }
}
