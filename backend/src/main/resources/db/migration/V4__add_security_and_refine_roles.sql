ALTER TABLE users DROP CONSTRAINT ck_users_system_role;

UPDATE users
SET system_role = 'SUPERUSER'
WHERE system_role = 'ADMIN';

ALTER TABLE users
    ADD CONSTRAINT ck_users_system_role
        CHECK (system_role IN ('USER', 'SUPERUSER'));

ALTER TABLE project_members DROP CONSTRAINT ck_project_members_role;

UPDATE project_members
SET role = 'OWNER'
WHERE role = 'HOST';

ALTER TABLE project_members
    ADD CONSTRAINT ck_project_members_role
        CHECK (role IN ('OWNER', 'COLLABORATOR', 'VIEWER'));

CREATE TABLE auth_refresh_tokens (
    id UUID NOT NULL,
    user_id UUID NOT NULL,
    token_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_auth_refresh_tokens PRIMARY KEY (id),
    CONSTRAINT uq_auth_refresh_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_auth_refresh_tokens_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE RESTRICT
);

CREATE INDEX idx_auth_refresh_tokens_user
    ON auth_refresh_tokens (user_id);

CREATE UNIQUE INDEX uq_project_members_one_active_owner
    ON project_members (project_id)
    WHERE role = 'OWNER' AND status = 'ACTIVE';
