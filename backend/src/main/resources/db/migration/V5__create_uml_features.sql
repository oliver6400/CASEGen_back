CREATE TABLE uml_properties (
    element_id UUID NOT NULL,
    owner_classifier_id UUID NOT NULL,
    visibility VARCHAR(20) NOT NULL DEFAULT 'PRIVATE',
    type_kind VARCHAR(20) NOT NULL,
    primitive_type VARCHAR(30) NULL,
    type_classifier_id UUID NULL,
    lower_bound INT NOT NULL DEFAULT 1,
    upper_bound INT NULL,
    is_static BOOLEAN NOT NULL DEFAULT FALSE,
    is_read_only BOOLEAN NOT NULL DEFAULT FALSE,
    is_derived BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_uml_properties PRIMARY KEY (element_id),
    CONSTRAINT ck_uml_properties_visibility CHECK (
        visibility IN ('PUBLIC', 'PRIVATE', 'PROTECTED', 'PACKAGE')
    ),
    CONSTRAINT ck_uml_properties_type_kind CHECK (
        type_kind IN ('PRIMITIVE', 'CLASSIFIER')
    ),
    CONSTRAINT ck_uml_properties_primitive_type CHECK (
        primitive_type IS NULL OR primitive_type IN (
            'STRING', 'INTEGER', 'BOOLEAN', 'REAL', 'UNLIMITED_NATURAL'
        )
    ),
    CONSTRAINT ck_uml_properties_type_xor CHECK (
        (type_kind = 'PRIMITIVE' AND primitive_type IS NOT NULL AND type_classifier_id IS NULL)
        OR
        (type_kind = 'CLASSIFIER' AND primitive_type IS NULL AND type_classifier_id IS NOT NULL)
    ),
    CONSTRAINT ck_uml_properties_multiplicity CHECK (
        lower_bound >= 0 AND (upper_bound IS NULL OR upper_bound >= lower_bound)
    ),
    CONSTRAINT fk_uml_properties_element FOREIGN KEY (element_id)
        REFERENCES uml_elements (id) ON DELETE RESTRICT,
    CONSTRAINT fk_uml_properties_owner FOREIGN KEY (owner_classifier_id)
        REFERENCES uml_classifiers (element_id) ON DELETE RESTRICT,
    CONSTRAINT fk_uml_properties_type_classifier FOREIGN KEY (type_classifier_id)
        REFERENCES uml_classifiers (element_id) ON DELETE RESTRICT
);

CREATE INDEX idx_uml_properties_owner
    ON uml_properties (owner_classifier_id);
CREATE INDEX idx_uml_properties_type_classifier
    ON uml_properties (type_classifier_id)
    WHERE type_classifier_id IS NOT NULL;

CREATE TABLE uml_operations (
    element_id UUID NOT NULL,
    owner_classifier_id UUID NOT NULL,
    visibility VARCHAR(20) NOT NULL DEFAULT 'PUBLIC',
    is_abstract BOOLEAN NOT NULL DEFAULT FALSE,
    is_static BOOLEAN NOT NULL DEFAULT FALSE,
    is_query BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_uml_operations PRIMARY KEY (element_id),
    CONSTRAINT ck_uml_operations_visibility CHECK (
        visibility IN ('PUBLIC', 'PRIVATE', 'PROTECTED', 'PACKAGE')
    ),
    CONSTRAINT fk_uml_operations_element FOREIGN KEY (element_id)
        REFERENCES uml_elements (id) ON DELETE RESTRICT,
    CONSTRAINT fk_uml_operations_owner FOREIGN KEY (owner_classifier_id)
        REFERENCES uml_classifiers (element_id) ON DELETE RESTRICT
);

CREATE INDEX idx_uml_operations_owner
    ON uml_operations (owner_classifier_id);

CREATE TABLE uml_parameters (
    element_id UUID NOT NULL,
    operation_id UUID NOT NULL,
    direction VARCHAR(20) NOT NULL,
    type_kind VARCHAR(20) NOT NULL,
    primitive_type VARCHAR(30) NULL,
    type_classifier_id UUID NULL,
    lower_bound INT NOT NULL DEFAULT 1,
    upper_bound INT NULL,
    position INT NOT NULL,
    CONSTRAINT pk_uml_parameters PRIMARY KEY (element_id),
    CONSTRAINT uq_uml_parameters_operation_position UNIQUE (operation_id, position),
    CONSTRAINT ck_uml_parameters_direction CHECK (
        direction IN ('IN', 'OUT', 'INOUT', 'RETURN')
    ),
    CONSTRAINT ck_uml_parameters_type_kind CHECK (
        type_kind IN ('PRIMITIVE', 'CLASSIFIER')
    ),
    CONSTRAINT ck_uml_parameters_primitive_type CHECK (
        primitive_type IS NULL OR primitive_type IN (
            'STRING', 'INTEGER', 'BOOLEAN', 'REAL', 'UNLIMITED_NATURAL'
        )
    ),
    CONSTRAINT ck_uml_parameters_type_xor CHECK (
        (type_kind = 'PRIMITIVE' AND primitive_type IS NOT NULL AND type_classifier_id IS NULL)
        OR
        (type_kind = 'CLASSIFIER' AND primitive_type IS NULL AND type_classifier_id IS NOT NULL)
    ),
    CONSTRAINT ck_uml_parameters_multiplicity CHECK (
        lower_bound >= 0 AND (upper_bound IS NULL OR upper_bound >= lower_bound)
    ),
    CONSTRAINT ck_uml_parameters_position CHECK (position >= 0),
    CONSTRAINT fk_uml_parameters_element FOREIGN KEY (element_id)
        REFERENCES uml_elements (id) ON DELETE RESTRICT,
    CONSTRAINT fk_uml_parameters_operation FOREIGN KEY (operation_id)
        REFERENCES uml_operations (element_id) ON DELETE RESTRICT,
    CONSTRAINT fk_uml_parameters_type_classifier FOREIGN KEY (type_classifier_id)
        REFERENCES uml_classifiers (element_id) ON DELETE RESTRICT
);

CREATE INDEX idx_uml_parameters_operation
    ON uml_parameters (operation_id);
CREATE INDEX idx_uml_parameters_type_classifier
    ON uml_parameters (type_classifier_id)
    WHERE type_classifier_id IS NOT NULL;
CREATE UNIQUE INDEX uq_uml_parameters_one_return
    ON uml_parameters (operation_id)
    WHERE direction = 'RETURN';
