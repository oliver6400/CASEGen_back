CREATE TABLE ai_proposal_batches (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    session_id UUID NOT NULL,
    source_message_id UUID NOT NULL,
    requested_by_user_id UUID NOT NULL,
    model_id UUID NOT NULL,
    diagram_id UUID NULL,
    title VARCHAR(180) NOT NULL,
    summary TEXT NOT NULL,
    base_model_revision BIGINT NOT NULL,
    base_diagram_revision BIGINT NULL,
    model_revision_cursor BIGINT NOT NULL,
    provider VARCHAR(30) NOT NULL,
    skill VARCHAR(30) NOT NULL,
    client_batch_id UUID NOT NULL,
    applied_by_user_id UUID NULL,
    applied_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    lock_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_ai_proposal_batches_project FOREIGN KEY (project_id)
        REFERENCES projects(id) ON DELETE CASCADE,
    CONSTRAINT fk_ai_proposal_batches_session FOREIGN KEY (session_id)
        REFERENCES ai_sessions(id) ON DELETE RESTRICT,
    CONSTRAINT fk_ai_proposal_batches_source_message FOREIGN KEY (source_message_id)
        REFERENCES ai_messages(id) ON DELETE RESTRICT,
    CONSTRAINT fk_ai_proposal_batches_requested_by FOREIGN KEY (requested_by_user_id)
        REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_ai_proposal_batches_model FOREIGN KEY (model_id)
        REFERENCES uml_models(id) ON DELETE RESTRICT,
    CONSTRAINT fk_ai_proposal_batches_diagram FOREIGN KEY (diagram_id)
        REFERENCES diagrams(id) ON DELETE SET NULL,
    CONSTRAINT fk_ai_proposal_batches_applied_by FOREIGN KEY (applied_by_user_id)
        REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT uq_ai_proposal_batches_project_client UNIQUE (project_id, client_batch_id),
    CONSTRAINT ck_ai_proposal_batches_title_not_blank CHECK (length(trim(title)) > 0),
    CONSTRAINT ck_ai_proposal_batches_summary_not_blank CHECK (length(trim(summary)) > 0),
    CONSTRAINT ck_ai_proposal_batches_provider_not_blank CHECK (length(trim(provider)) > 0),
    CONSTRAINT ck_ai_proposal_batches_skill CHECK (skill = 'UML_REVIEWER'),
    CONSTRAINT ck_ai_proposal_batches_base_model_revision CHECK (base_model_revision >= 0),
    CONSTRAINT ck_ai_proposal_batches_base_diagram_revision CHECK (
        base_diagram_revision IS NULL OR base_diagram_revision >= 0
    ),
    CONSTRAINT ck_ai_proposal_batches_revision_cursor CHECK (
        model_revision_cursor >= base_model_revision
    ),
    CONSTRAINT ck_ai_proposal_batches_apply_audit CHECK (
        (applied_by_user_id IS NULL AND applied_at IS NULL)
        OR (applied_by_user_id IS NOT NULL AND applied_at IS NOT NULL)
    )
);

CREATE INDEX idx_ai_proposal_batches_project_created
    ON ai_proposal_batches(project_id, created_at DESC, id DESC);
CREATE INDEX idx_ai_proposal_batches_source_message ON ai_proposal_batches(source_message_id);
CREATE INDEX idx_ai_proposal_batches_session ON ai_proposal_batches(session_id);
CREATE INDEX idx_ai_proposal_batches_model ON ai_proposal_batches(model_id);
CREATE INDEX idx_ai_proposal_batches_diagram ON ai_proposal_batches(diagram_id);

ALTER TABLE ai_proposals
    ADD COLUMN batch_id UUID NULL,
    ADD COLUMN batch_order INTEGER NULL,
    ADD COLUMN client_ref VARCHAR(80) NULL,
    ADD CONSTRAINT fk_ai_proposals_batch FOREIGN KEY (batch_id)
        REFERENCES ai_proposal_batches(id) ON DELETE CASCADE,
    ADD CONSTRAINT ck_ai_proposals_batch_shape CHECK (
        (batch_id IS NULL AND batch_order IS NULL AND client_ref IS NULL)
        OR
        (batch_id IS NOT NULL AND batch_order IS NOT NULL AND client_ref IS NOT NULL)
    ),
    ADD CONSTRAINT ck_ai_proposals_batch_order CHECK (
        batch_order IS NULL OR (batch_order BETWEEN 1 AND 15)
    ),
    ADD CONSTRAINT ck_ai_proposals_client_ref CHECK (
        client_ref IS NULL OR client_ref ~ '^[a-z0-9][a-z0-9-]*$'
    );

CREATE UNIQUE INDEX uq_ai_proposals_batch_order
    ON ai_proposals(batch_id, batch_order) WHERE batch_id IS NOT NULL;
CREATE UNIQUE INDEX uq_ai_proposals_batch_client_ref
    ON ai_proposals(batch_id, client_ref) WHERE batch_id IS NOT NULL;
CREATE INDEX idx_ai_proposals_batch ON ai_proposals(batch_id) WHERE batch_id IS NOT NULL;

CREATE TABLE ai_proposal_dependencies (
    proposal_id UUID NOT NULL,
    depends_on_proposal_id UUID NOT NULL,
    PRIMARY KEY (proposal_id, depends_on_proposal_id),
    CONSTRAINT fk_ai_proposal_dependencies_proposal FOREIGN KEY (proposal_id)
        REFERENCES ai_proposals(id) ON DELETE CASCADE,
    CONSTRAINT fk_ai_proposal_dependencies_depends_on FOREIGN KEY (depends_on_proposal_id)
        REFERENCES ai_proposals(id) ON DELETE CASCADE,
    CONSTRAINT ck_ai_proposal_dependencies_not_self CHECK (proposal_id <> depends_on_proposal_id)
);

CREATE INDEX idx_ai_proposal_dependencies_depends_on
    ON ai_proposal_dependencies(depends_on_proposal_id);
