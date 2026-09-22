CREATE TABLE generation_runs (
 id UUID PRIMARY KEY,
 logical_model_id UUID NOT NULL REFERENCES logical_models(id),
 model_version_id UUID NOT NULL REFERENCES model_versions(id),
 generation_profile_id UUID NOT NULL REFERENCES generation_profiles(id),
 requested_by UUID NOT NULL REFERENCES users(id),
 generator_version VARCHAR(40) NOT NULL,
 status VARCHAR(30) NOT NULL CHECK (status IN ('QUEUED','GENERATING','COMPILING','TESTING','VERIFYING_DATABASE','PACKAGING','SUCCESS','FAILED')),
 current_stage VARCHAR(60),
 error_message TEXT,
 started_at TIMESTAMPTZ,
 finished_at TIMESTAMPTZ,
 created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE INDEX idx_generation_runs_logical_model ON generation_runs(logical_model_id,created_at DESC);

CREATE TABLE generation_artifacts (
 id UUID PRIMARY KEY,
 generation_run_id UUID NOT NULL REFERENCES generation_runs(id) ON DELETE CASCADE,
 artifact_type VARCHAR(40) NOT NULL,
 storage_key VARCHAR(500) NOT NULL UNIQUE,
 file_name VARCHAR(255) NOT NULL,
 checksum VARCHAR(64) NOT NULL,
 size_bytes BIGINT NOT NULL CHECK (size_bytes >= 0),
 created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE TABLE generation_test_runs (
 id UUID PRIMARY KEY,
 generation_run_id UUID NOT NULL REFERENCES generation_runs(id) ON DELETE CASCADE,
 test_type VARCHAR(40) NOT NULL,
 status VARCHAR(20) NOT NULL CHECK (status IN ('RUNNING','SUCCESS','FAILED','SKIPPED')),
 output_log TEXT,
 started_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 finished_at TIMESTAMPTZ
);
