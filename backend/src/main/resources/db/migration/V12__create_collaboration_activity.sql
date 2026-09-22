CREATE TABLE collaboration_activity (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    model_id UUID NOT NULL REFERENCES uml_models(id) ON DELETE CASCADE,
    diagram_id UUID NULL REFERENCES diagrams(id) ON DELETE CASCADE,
    actor_user_id UUID NOT NULL REFERENCES users(id),
    event_type VARCHAR(60) NOT NULL,
    element_id UUID NULL,
    description VARCHAR(500) NOT NULL,
    model_revision BIGINT NULL,
    diagram_revision BIGINT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_collaboration_activity_diagram_created
    ON collaboration_activity(diagram_id, created_at DESC);

CREATE INDEX idx_collaboration_activity_model_created
    ON collaboration_activity(model_id, created_at DESC);
