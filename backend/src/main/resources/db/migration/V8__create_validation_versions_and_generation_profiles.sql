CREATE TABLE validation_runs (
 id UUID PRIMARY KEY,
 model_id UUID NOT NULL REFERENCES uml_models(id),
 model_revision BIGINT NOT NULL CHECK (model_revision >= 0),
 generation_checksum VARCHAR(64) NOT NULL,
 status VARCHAR(20) NOT NULL CHECK (status IN ('RUNNING','VALID','INVALID')),
 created_by UUID REFERENCES users(id),
 started_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 finished_at TIMESTAMPTZ
);
CREATE INDEX idx_validation_runs_model ON validation_runs(model_id, started_at DESC);
CREATE TABLE validation_issues (
 id UUID PRIMARY KEY,
 validation_run_id UUID NOT NULL REFERENCES validation_runs(id),
 uml_element_id UUID,
 severity VARCHAR(20) NOT NULL CHECK (severity IN ('ERROR','WARNING')),
 code VARCHAR(100) NOT NULL,
 message TEXT NOT NULL,
 path VARCHAR(500)
);
-- Element IDs intentionally survive deletion from the editable model.
CREATE INDEX idx_validation_issues_run ON validation_issues(validation_run_id);
CREATE TABLE model_versions (
 id UUID PRIMARY KEY,
 model_id UUID NOT NULL REFERENCES uml_models(id),
 validation_run_id UUID NOT NULL REFERENCES validation_runs(id),
 version_number BIGINT NOT NULL CHECK (version_number > 0),
 created_by UUID REFERENCES users(id),
 semantic_snapshot JSONB NOT NULL,
 diagram_snapshot JSONB,
 checksum VARCHAR(64) NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(model_id, version_number)
);
CREATE FUNCTION prevent_model_version_changes() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 RAISE EXCEPTION 'ModelVersion is immutable';
END;
$$;
CREATE TRIGGER immutable_model_versions BEFORE UPDATE OR DELETE ON model_versions
 FOR EACH ROW EXECUTE FUNCTION prevent_model_version_changes();
CREATE TABLE generation_profiles (
 id UUID PRIMARY KEY,
 model_id UUID NOT NULL REFERENCES uml_models(id),
 name VARCHAR(180) NOT NULL CHECK (length(trim(name)) > 0),
 target VARCHAR(30) NOT NULL CHECK (target = 'SPRING_BOOT'),
 group_id VARCHAR(180) NOT NULL,
 artifact_id VARCHAR(180) NOT NULL,
 base_package VARCHAR(180) NOT NULL,
 java_version INT NOT NULL CHECK (java_version >= 21),
 spring_boot_version VARCHAR(40) NOT NULL,
 database VARCHAR(30) NOT NULL CHECK (database = 'POSTGRESQL'),
 config_json JSONB NOT NULL DEFAULT '{}'::jsonb CHECK (jsonb_typeof(config_json) = 'object'),
 UNIQUE(model_id, name)
);
CREATE TABLE generation_element_mappings (
 id UUID PRIMARY KEY,
 generation_profile_id UUID NOT NULL REFERENCES generation_profiles(id) ON DELETE CASCADE,
 uml_element_id UUID NOT NULL REFERENCES uml_elements(id) ON DELETE CASCADE,
 target_kind VARCHAR(30) NOT NULL CHECK (target_kind IN ('ENTITY','VALUE_OBJECT','SERVICE','JAVA_INTERFACE','IGNORE')),
 config_json JSONB NOT NULL DEFAULT '{}'::jsonb CHECK (jsonb_typeof(config_json) = 'object'),
 UNIQUE(generation_profile_id, uml_element_id)
);
