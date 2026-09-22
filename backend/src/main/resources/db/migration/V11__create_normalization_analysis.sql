CREATE TABLE normalization_runs (
    id UUID PRIMARY KEY,
    logical_model_id UUID NOT NULL REFERENCES logical_models(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PASS','WARNING','FAIL','UNKNOWN')),
    highest_normal_form VARCHAR(10),
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE INDEX idx_normalization_runs_logical_model
    ON normalization_runs(logical_model_id, created_at DESC, id DESC);

CREATE TABLE normalization_issues (
    id UUID PRIMARY KEY,
    normalization_run_id UUID NOT NULL REFERENCES normalization_runs(id) ON DELETE CASCADE,
    logical_table_id UUID REFERENCES logical_tables(id) ON DELETE SET NULL,
    logical_column_id UUID REFERENCES logical_columns(id) ON DELETE SET NULL,
    normal_form VARCHAR(10) NOT NULL CHECK (normal_form IN ('1NF','2NF','3NF')),
    severity VARCHAR(20) NOT NULL CHECK (severity IN ('WARNING','ERROR')),
    code VARCHAR(100) NOT NULL,
    message TEXT NOT NULL,
    recommendation TEXT NOT NULL,
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb CHECK (jsonb_typeof(metadata_json) = 'object')
);

CREATE INDEX idx_normalization_issues_run
    ON normalization_issues(normalization_run_id, normal_form, code, id);
