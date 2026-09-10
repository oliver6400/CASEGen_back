CREATE UNIQUE INDEX uq_users_single_superuser
    ON users (system_role)
    WHERE system_role = 'SUPERUSER';

CREATE TABLE system_bootstrap (
    id SMALLINT NOT NULL,
    superuser_created BOOLEAN NOT NULL DEFAULT FALSE,
    superuser_user_id UUID NULL,
    completed_at TIMESTAMPTZ NULL,
    CONSTRAINT pk_system_bootstrap PRIMARY KEY (id),
    CONSTRAINT ck_system_bootstrap_singleton CHECK (id = 1),
    CONSTRAINT ck_system_bootstrap_state CHECK (
        (superuser_created = FALSE AND superuser_user_id IS NULL AND completed_at IS NULL)
        OR
        (superuser_created = TRUE AND superuser_user_id IS NOT NULL AND completed_at IS NOT NULL)
    ),
    CONSTRAINT fk_system_bootstrap_superuser FOREIGN KEY (superuser_user_id)
        REFERENCES users (id) ON DELETE RESTRICT
);

INSERT INTO system_bootstrap (
    id,
    superuser_created,
    superuser_user_id,
    completed_at
)
SELECT
    1,
    EXISTS (SELECT 1 FROM users WHERE system_role = 'SUPERUSER'),
    (SELECT id FROM users WHERE system_role = 'SUPERUSER' ORDER BY created_at, id LIMIT 1),
    CASE
        WHEN EXISTS (SELECT 1 FROM users WHERE system_role = 'SUPERUSER') THEN CURRENT_TIMESTAMP
        ELSE NULL
    END;
