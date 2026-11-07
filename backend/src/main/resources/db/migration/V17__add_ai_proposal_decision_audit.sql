ALTER TABLE ai_proposals
    ADD COLUMN decided_by_user_id UUID NULL,
    ADD COLUMN decided_at TIMESTAMPTZ NULL,
    ADD COLUMN decision_note TEXT NULL,
    ADD COLUMN applied_model_revision BIGINT NULL,
    ADD COLUMN applied_element_id UUID NULL;

ALTER TABLE ai_proposals
    ADD CONSTRAINT fk_ai_proposals_decided_by
        FOREIGN KEY (decided_by_user_id)
        REFERENCES users(id)
        ON DELETE RESTRICT,
    ADD CONSTRAINT ck_ai_proposals_decision_note_length
        CHECK (decision_note IS NULL OR char_length(decision_note) <= 1000),
    ADD CONSTRAINT ck_ai_proposals_applied_model_revision
        CHECK (applied_model_revision IS NULL OR applied_model_revision >= 0),
    ADD CONSTRAINT ck_ai_proposals_decision_state
        CHECK (
            (status = 'PENDING'
                AND decided_by_user_id IS NULL
                AND decided_at IS NULL
                AND applied_model_revision IS NULL
                AND applied_element_id IS NULL)
            OR
            (status = 'REJECTED'
                AND decided_by_user_id IS NOT NULL
                AND decided_at IS NOT NULL
                AND applied_model_revision IS NULL
                AND applied_element_id IS NULL)
            OR
            (status = 'APPLIED'
                AND decided_by_user_id IS NOT NULL
                AND decided_at IS NOT NULL
                AND applied_model_revision IS NOT NULL
                AND applied_element_id IS NOT NULL)
        );

CREATE INDEX idx_ai_proposals_decided_by
    ON ai_proposals(decided_by_user_id);
