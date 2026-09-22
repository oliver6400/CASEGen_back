package com.casegen.generation.normalization.repository;

import com.casegen.generation.normalization.dto.NormalizationDtos.IssueResponse;
import com.casegen.generation.normalization.dto.NormalizationDtos.Response;
import com.casegen.generation.normalization.service.NormalizationAnalyzer.Issue;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class NormalizationRepository {
    public record Source(UUID logicalModelId, UUID projectId) {}
    public record References(Map<String, UUID> tables, Map<String, Map<String, UUID>> columns) {
        public UUID table(String name) { return tables.get(name); }
        public UUID column(String table, String name) {
            return name == null ? null : columns.getOrDefault(table, Map.of()).get(name);
        }
    }

    private final JdbcTemplate jdbc;
    private final JsonMapper json = JsonMapper.builder().build();

    public NormalizationRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<Source> sourceByLogicalModel(UUID logicalModelId) {
        return jdbc.query("""
                SELECT l.id, m.project_id
                FROM logical_models l
                JOIN model_versions v ON v.id=l.model_version_id
                JOIN uml_models m ON m.id=v.model_id
                WHERE l.id=?
                """, (row, index) -> new Source(row.getObject(1, UUID.class), row.getObject(2, UUID.class)), logicalModelId)
                .stream().findFirst();
    }

    public Optional<Source> sourceByRun(UUID runId) {
        return jdbc.query("""
                SELECT n.logical_model_id, m.project_id
                FROM normalization_runs n
                JOIN logical_models l ON l.id=n.logical_model_id
                JOIN model_versions v ON v.id=l.model_version_id
                JOIN uml_models m ON m.id=v.model_id
                WHERE n.id=?
                """, (row, index) -> new Source(row.getObject(1, UUID.class), row.getObject(2, UUID.class)), runId)
                .stream().findFirst();
    }

    public References references(UUID logicalModelId) {
        Map<String, UUID> tables = new LinkedHashMap<>();
        Map<String, Map<String, UUID>> columns = new LinkedHashMap<>();
        jdbc.query("SELECT id,name FROM logical_tables WHERE logical_model_id=? ORDER BY name", (RowCallbackHandler) row -> {
            UUID tableId = row.getObject("id", UUID.class); String tableName = row.getString("name");
            tables.put(tableName, tableId); Map<String, UUID> tableColumns = new LinkedHashMap<>();
            jdbc.query("SELECT id,name FROM logical_columns WHERE table_id=? ORDER BY ordinal_position", (RowCallbackHandler) column ->
                    tableColumns.put(column.getString("name"), column.getObject("id", UUID.class)), tableId);
            columns.put(tableName, tableColumns);
        }, logicalModelId);
        return new References(Map.copyOf(tables), columns.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                Map.Entry::getKey, entry -> Map.copyOf(entry.getValue()))));
    }

    public void append(UUID runId, UUID logicalModelId, String status, String highestNormalForm,
            UUID actor, List<Issue> issues, References references) {
        jdbc.update("INSERT INTO normalization_runs(id,logical_model_id,status,highest_normal_form,created_by) VALUES(?,?,?,?,?)",
                runId, logicalModelId, status, highestNormalForm, actor);
        for (Issue issue : issues) {
            jdbc.update("""
                    INSERT INTO normalization_issues(id,normalization_run_id,logical_table_id,logical_column_id,
                        normal_form,severity,code,message,recommendation,metadata_json)
                    VALUES(?,?,?,?,?,?,?,?,?,?::jsonb)
                    """, UUID.randomUUID(), runId, references.table(issue.tableName()),
                    references.column(issue.tableName(), issue.columnName()), issue.normalForm(), issue.severity(),
                    issue.code(), issue.message(), issue.recommendation(), json.writeValueAsString(issue.metadata()));
        }
    }

    public Optional<Response> find(UUID runId) {
        return jdbc.query("SELECT * FROM normalization_runs WHERE id=?", (row, index) -> new Response(
                row.getObject("id", UUID.class), row.getObject("logical_model_id", UUID.class), row.getString("status"),
                row.getString("highest_normal_form"), row.getObject("created_by", UUID.class),
                row.getTimestamp("created_at").toInstant(), List.of(), issues(runId)), runId).stream().findFirst();
    }

    public List<Response> list(UUID logicalModelId) {
        return jdbc.query("SELECT * FROM normalization_runs WHERE logical_model_id=? ORDER BY created_at DESC,id DESC",
                (row, index) -> { UUID id = row.getObject("id", UUID.class); return new Response(id, logicalModelId,
                    row.getString("status"), row.getString("highest_normal_form"), row.getObject("created_by", UUID.class),
                    row.getTimestamp("created_at").toInstant(), List.of(), issues(id)); }, logicalModelId);
    }

    private List<IssueResponse> issues(UUID runId) {
        return jdbc.query("""
                SELECT i.*,t.name AS table_name,c.name AS column_name
                FROM normalization_issues i
                LEFT JOIN logical_tables t ON t.id=i.logical_table_id
                LEFT JOIN logical_columns c ON c.id=i.logical_column_id
                WHERE i.normalization_run_id=?
                ORDER BY i.normal_form,t.name,i.code,i.metadata_json->>'columns',i.id
                """, (row, index) -> {
                    JsonNode metadata = json.readTree(row.getString("metadata_json"));
                    return new IssueResponse(row.getObject("id", UUID.class), row.getObject("logical_table_id", UUID.class),
                            row.getString("table_name"), row.getObject("logical_column_id", UUID.class),
                            row.getString("column_name"), row.getString("normal_form"), row.getString("severity"),
                            row.getString("code"), row.getString("message"), row.getString("recommendation"), metadata,
                            metadata.path("sourceElementId").asText(null));
                }, runId);
    }
}
