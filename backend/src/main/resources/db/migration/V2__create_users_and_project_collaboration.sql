CREATE TABLE users (
    id UUID NOT NULL,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    system_role VARCHAR(20) NOT NULL DEFAULT 'USER',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT ck_users_system_role CHECK (system_role IN ('USER', 'ADMIN'))
);

-- CASEGen treats account emails as case-insensitive identifiers.
CREATE UNIQUE INDEX uq_users_email_lower ON users (lower(email));

CREATE TABLE project_members (
    id UUID NOT NULL,
    project_id UUID NOT NULL,
    user_id UUID NOT NULL,
    role VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    joined_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_project_members PRIMARY KEY (id),
    CONSTRAINT uq_project_members_project_user UNIQUE (project_id, user_id),
    CONSTRAINT ck_project_members_role CHECK (role IN ('HOST', 'COLLABORATOR', 'VIEWER')),
    CONSTRAINT ck_project_members_status CHECK (status IN ('INVITED', 'ACTIVE', 'REVOKED')),
    CONSTRAINT fk_project_members_project FOREIGN KEY (project_id)
        REFERENCES projects (id) ON DELETE RESTRICT,
    CONSTRAINT fk_project_members_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE RESTRICT
);

CREATE INDEX idx_project_members_project ON project_members (project_id);
CREATE INDEX idx_project_members_user ON project_members (user_id);

CREATE TABLE project_invitations (
    id UUID NOT NULL,
    project_id UUID NOT NULL,
    invited_by UUID NOT NULL,
    email VARCHAR(254) NOT NULL,
    role VARCHAR(20) NOT NULL,
    token_hash VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    accepted_at TIMESTAMPTZ NULL,
    CONSTRAINT pk_project_invitations PRIMARY KEY (id),
    CONSTRAINT uq_project_invitations_token_hash UNIQUE (token_hash),
    CONSTRAINT ck_project_invitations_role CHECK (role IN ('COLLABORATOR', 'VIEWER')),
    CONSTRAINT ck_project_invitations_status CHECK (status IN ('PENDING', 'ACCEPTED', 'EXPIRED', 'REVOKED')),
    CONSTRAINT fk_project_invitations_project FOREIGN KEY (project_id)
        REFERENCES projects (id) ON DELETE RESTRICT,
    CONSTRAINT fk_project_invitations_invited_by FOREIGN KEY (invited_by)
        REFERENCES users (id) ON DELETE RESTRICT
);

CREATE INDEX idx_project_invitations_project ON project_invitations (project_id);
CREATE INDEX idx_project_invitations_email ON project_invitations (email);
