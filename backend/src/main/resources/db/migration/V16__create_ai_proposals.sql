CREATE TABLE ai_proposals (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    session_id UUID NOT NULL,
    source_message_id UUID NOT NULL,
    requested_by_user_id UUID NOT NULL,
    model_id UUID NULL,
    diagram_id UUID NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    proposal_type VARCHAR(40) NOT NULL,
    title VARCHAR(180) NOT NULL,
    rationale TEXT NOT NULL,
    payload JSONB NOT NULL,
    payload_schema_version SMALLINT NOT NULL DEFAULT 1,
    base_model_revision BIGINT NULL,
    base_diagram_revision BIGINT NULL,
    provider VARCHAR(30) NOT NULL,
    skill VARCHAR(30) NOT NULL,
    client_proposal_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_ai_proposals_project FOREIGN KEY (project_id)
        REFERENCES projects(id) ON DELETE CASCADE,
    CONSTRAINT fk_ai_proposals_session FOREIGN KEY (session_id)
        REFERENCES ai_sessions(id) ON DELETE RESTRICT,
    CONSTRAINT fk_ai_proposals_source_message FOREIGN KEY (source_message_id)
        REFERENCES ai_messages(id) ON DELETE RESTRICT,
    CONSTRAINT fk_ai_proposals_requested_by FOREIGN KEY (requested_by_user_id)
        REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_ai_proposals_model FOREIGN KEY (model_id)
        REFERENCES uml_models(id) ON DELETE SET NULL,
    CONSTRAINT fk_ai_proposals_diagram FOREIGN KEY (diagram_id)
        REFERENCES diagrams(id) ON DELETE SET NULL,
    CONSTRAINT uq_ai_proposals_project_client UNIQUE (project_id, client_proposal_id),
    CONSTRAINT ck_ai_proposals_status CHECK (status IN ('PENDING', 'APPLIED', 'REJECTED')),
    CONSTRAINT ck_ai_proposals_type CHECK (proposal_type IN (
        'ADD_CLASSIFIER', 'ADD_ATTRIBUTE', 'ADD_OPERATION',
        'ADD_ASSOCIATION', 'ADD_GENERALIZATION', 'ADD_REALIZATION'
    )),
    CONSTRAINT ck_ai_proposals_skill CHECK (skill IN (
        'GENERAL', 'UML_REVIEWER', 'DATABASE_REVIEWER'
    )),
    CONSTRAINT ck_ai_proposals_title_not_blank CHECK (length(trim(title)) > 0),
    CONSTRAINT ck_ai_proposals_rationale_not_blank CHECK (length(trim(rationale)) > 0),
    CONSTRAINT ck_ai_proposals_payload_object CHECK (jsonb_typeof(payload) = 'object'),
    CONSTRAINT ck_ai_proposals_payload_schema_version CHECK (payload_schema_version > 0),
    CONSTRAINT ck_ai_proposals_base_model_revision CHECK (
        base_model_revision IS NULL OR base_model_revision >= 0
    ),
    CONSTRAINT ck_ai_proposals_base_diagram_revision CHECK (
        base_diagram_revision IS NULL OR base_diagram_revision >= 0
    )
);

CREATE INDEX idx_ai_proposals_project_status_created
    ON ai_proposals(project_id, status, created_at DESC, id DESC);

CREATE INDEX idx_ai_proposals_source_message
    ON ai_proposals(source_message_id);

CREATE INDEX idx_ai_proposals_session
    ON ai_proposals(session_id);

CREATE INDEX idx_ai_proposals_model
    ON ai_proposals(model_id);
