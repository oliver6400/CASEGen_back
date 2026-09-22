-- Creation order must not depend on timestamps: clock corrections can leave
-- historical validations dated in the future.
CREATE SEQUENCE validation_runs_run_order_seq;
ALTER TABLE validation_runs ADD COLUMN run_order BIGINT;

-- Recover historical semantic order without changing timestamps or snapshots.
WITH ordered_runs AS (
    SELECT id, row_number() OVER (
        ORDER BY model_id, model_revision, started_at, id
    ) AS run_order
    FROM validation_runs
)
UPDATE validation_runs AS runs
SET run_order = ordered_runs.run_order
FROM ordered_runs
WHERE runs.id = ordered_runs.id;

SELECT setval('validation_runs_run_order_seq',
    COALESCE(MAX(run_order), 1), COUNT(*) > 0)
FROM validation_runs;

ALTER SEQUENCE validation_runs_run_order_seq OWNED BY validation_runs.run_order;
ALTER TABLE validation_runs
    ALTER COLUMN run_order SET DEFAULT nextval('validation_runs_run_order_seq'),
    ALTER COLUMN run_order SET NOT NULL,
    ADD CONSTRAINT validation_runs_run_order_unique UNIQUE (run_order);
CREATE INDEX validation_runs_latest_order_idx ON validation_runs (model_id, run_order DESC);
