CREATE TABLE ai_sessions (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    host_user_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_ai_sessions_project FOREIGN KEY (project_id)
        REFERENCES projects(id) ON DELETE CASCADE,
    CONSTRAINT fk_ai_sessions_host_user FOREIGN KEY (host_user_id)
        REFERENCES users(id),
    CONSTRAINT ck_ai_sessions_status CHECK (status IN ('ACTIVE', 'CLOSED')),
    CONSTRAINT ck_ai_sessions_end_state CHECK (
        (status = 'ACTIVE' AND ended_at IS NULL)
        OR (status = 'CLOSED' AND ended_at IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uq_ai_sessions_one_active_per_project
    ON ai_sessions(project_id)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_ai_sessions_project_started
    ON ai_sessions(project_id, started_at DESC);

CREATE TABLE ai_session_participants (
    session_id UUID NOT NULL,
    user_id UUID NOT NULL,
    joined_at TIMESTAMPTZ NOT NULL,
    last_seen_at TIMESTAMPTZ NULL,
    CONSTRAINT pk_ai_session_participants PRIMARY KEY (session_id, user_id),
    CONSTRAINT fk_ai_session_participants_session FOREIGN KEY (session_id)
        REFERENCES ai_sessions(id) ON DELETE CASCADE,
    CONSTRAINT fk_ai_session_participants_user FOREIGN KEY (user_id)
        REFERENCES users(id)
);

CREATE INDEX idx_ai_session_participants_user
    ON ai_session_participants(user_id);
