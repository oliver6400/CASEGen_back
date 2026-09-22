package com.casegen.modeling.service;

import com.casegen.modeling.domain.UmlModel;
import com.casegen.modeling.domain.UmlModelStatus;
import com.casegen.modeling.repository.UmlModelRepository;
import com.casegen.project.service.ProjectAuthorizationService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Read-only, schema-versioned snapshot boundary; never exposes JPA objects. */
@Service
public class ModelSnapshotReader {
    private final UmlModelRepository models;
    private final ProjectAuthorizationService authorization;
    private final JdbcTemplate jdbc;
    private final JsonMapper json = JsonMapper.builder().build();

    public ModelSnapshotReader(UmlModelRepository models, ProjectAuthorizationService authorization, JdbcTemplate jdbc) {
        this.models = models;
        this.authorization = authorization;
        this.jdbc = jdbc;
    }

    public UmlModel requireModel(UUID id, boolean write) {
        UmlModel model = (write ? models.findByIdForUpdate(id) : models.findById(id))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Model not found"));
        if (write) {
            authorization.requireWrite(model.getProject().getId());
            if (model.getStatus() != UmlModelStatus.EDITABLE) throw new ResponseStatusException(HttpStatus.CONFLICT, "Model is not editable");
        } else authorization.requireRead(model.getProject().getId());
        return model;
    }

    public UUID actor() { return authorization.currentUser().getId(); }
    public JsonNode parse(String value) { return json.readTree(value); }
    public String stringify(JsonNode value) { return json.writeValueAsString(value); }
    public String generation(UUID modelId) {
        return jdbc.queryForObject("""
            SELECT jsonb_build_object(
              'profiles', COALESCE((SELECT jsonb_agg(to_jsonb(p) ORDER BY p.id) FROM generation_profiles p WHERE p.model_id = ?), '[]'::jsonb),
              'mappings', COALESCE((SELECT jsonb_agg(to_jsonb(m) ORDER BY m.id) FROM generation_element_mappings m
                 JOIN generation_profiles p ON p.id=m.generation_profile_id WHERE p.model_id = ?), '[]'::jsonb))::text
            """, String.class, modelId, modelId);
    }

    public String semantic(UUID modelId) {
        var parts = new java.util.ArrayList<String>();
        parts.add("'schemaVersion', 1");
        parts.add("'model', (SELECT to_jsonb(m) - 'created_at' - 'updated_at' - 'lock_version' - 'created_by' FROM uml_models m WHERE m.id='" + modelId + "')");
        parts.add("'elements', (SELECT COALESCE(jsonb_agg(to_jsonb(e) - 'created_at' - 'updated_at' ORDER BY e.id), '[]'::jsonb) FROM uml_elements e WHERE e.model_id='" + modelId + "')");
        for (String table : List.of("packages", "classifiers", "properties", "operations", "parameters", "associations", "generalizations", "realizations")) {
            parts.add("'" + table + "', (SELECT COALESCE(jsonb_agg(to_jsonb(t) ORDER BY t.element_id), '[]'::jsonb) FROM uml_" + table + " t JOIN uml_elements e ON e.id=t.element_id WHERE e.model_id='" + modelId + "')");
        }
        parts.add("'associationEnds', (SELECT COALESCE(jsonb_agg(to_jsonb(t) ORDER BY t.association_id, t.member_order), '[]'::jsonb) FROM uml_association_ends t JOIN uml_elements e ON e.id=t.association_id WHERE e.model_id='" + modelId + "')");
        parts.add("'generation', '" + generation(modelId).replace("'", "''") + "'::jsonb");
        // Identifiers/table names above are server-owned constants, modelId is a UUID.
        return jdbc.queryForObject("SELECT jsonb_build_object(" + String.join(",", parts) + ")::text", String.class);
    }

    public String diagrams(UUID modelId) {
        return jdbc.queryForObject("""
            SELECT jsonb_build_object('schemaVersion',1,
              'diagrams',COALESCE((SELECT jsonb_agg(to_jsonb(d) - 'created_at' - 'updated_at' - 'lock_version' ORDER BY d.id) FROM diagrams d WHERE d.model_id=?),'[]'::jsonb),
              'nodes',COALESCE((SELECT jsonb_agg(to_jsonb(n) - 'created_at' - 'updated_at' ORDER BY n.id) FROM diagram_nodes n JOIN diagrams d ON d.id=n.diagram_id WHERE d.model_id=?),'[]'::jsonb),
              'edges',COALESCE((SELECT jsonb_agg(to_jsonb(e) - 'created_at' - 'updated_at' ORDER BY e.id) FROM diagram_edges e JOIN diagrams d ON d.id=e.diagram_id WHERE d.model_id=?),'[]'::jsonb))::text
            """, String.class, modelId, modelId, modelId);
    }

    public static String checksum(String canonicalJson) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonicalJson.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
}
