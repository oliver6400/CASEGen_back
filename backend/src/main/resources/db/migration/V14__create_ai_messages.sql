CREATE TABLE ai_messages (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL,
    author_user_id UUID NULL,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    model_id UUID NULL,
    diagram_id UUID NULL,
    provider VARCHAR(30) NULL,
    client_message_id UUID NULL,
    reply_to_message_id UUID NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_ai_messages_session FOREIGN KEY (session_id)
        REFERENCES ai_sessions(id) ON DELETE CASCADE,
    CONSTRAINT fk_ai_messages_author_user FOREIGN KEY (author_user_id)
        REFERENCES users(id),
    CONSTRAINT fk_ai_messages_model FOREIGN KEY (model_id)
        REFERENCES uml_models(id) ON DELETE SET NULL,
    CONSTRAINT fk_ai_messages_diagram FOREIGN KEY (diagram_id)
        REFERENCES diagrams(id) ON DELETE SET NULL,
    CONSTRAINT fk_ai_messages_reply_to FOREIGN KEY (reply_to_message_id)
        REFERENCES ai_messages(id) ON DELETE SET NULL,
    CONSTRAINT ck_ai_messages_role CHECK (role IN ('USER', 'ASSISTANT')),
    CONSTRAINT ck_ai_messages_content_not_blank CHECK (length(trim(content)) > 0),
    CONSTRAINT ck_ai_messages_role_fields CHECK (
        (role = 'USER'
            AND author_user_id IS NOT NULL
            AND provider IS NULL
            AND reply_to_message_id IS NULL)
        OR
        (role = 'ASSISTANT'
            AND author_user_id IS NULL
            AND provider IS NOT NULL
            AND length(trim(provider)) > 0
            AND client_message_id IS NULL)
    )
);

CREATE INDEX idx_ai_messages_session_history
    ON ai_messages(session_id, created_at, id);

CREATE UNIQUE INDEX uq_ai_messages_session_client_message
    ON ai_messages(session_id, client_message_id)
    WHERE client_message_id IS NOT NULL;

CREATE UNIQUE INDEX uq_ai_messages_assistant_reply
    ON ai_messages(reply_to_message_id)
    WHERE role = 'ASSISTANT' AND reply_to_message_id IS NOT NULL;
