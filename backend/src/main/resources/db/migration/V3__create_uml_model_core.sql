CREATE TABLE uml_models (
    id UUID NOT NULL,
    project_id UUID NOT NULL,
    name VARCHAR(180) NOT NULL,
    documentation TEXT NULL,
    uml_version VARCHAR(20) NOT NULL DEFAULT '2.5.1',
    revision BIGINT NOT NULL DEFAULT 0,
    lock_version BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'EDITABLE',
    created_by UUID NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_uml_models PRIMARY KEY (id),
    CONSTRAINT uq_uml_models_project_name UNIQUE (project_id, name),
    CONSTRAINT ck_uml_models_revision CHECK (revision >= 0),
    CONSTRAINT ck_uml_models_lock_version CHECK (lock_version >= 0),
    CONSTRAINT ck_uml_models_status CHECK (status IN ('EDITABLE', 'ARCHIVED')),
    CONSTRAINT fk_uml_models_project FOREIGN KEY (project_id)
        REFERENCES projects (id) ON DELETE RESTRICT,
    CONSTRAINT fk_uml_models_created_by FOREIGN KEY (created_by)
        REFERENCES users (id) ON DELETE RESTRICT
);

CREATE INDEX idx_uml_models_project ON uml_models (project_id);

CREATE TABLE uml_elements (
    id UUID NOT NULL,
    model_id UUID NOT NULL,
    element_type VARCHAR(40) NOT NULL,
    name VARCHAR(180) NULL,
    documentation TEXT NULL,
    external_xmi_id VARCHAR(255) NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_uml_elements PRIMARY KEY (id),
    CONSTRAINT ck_uml_elements_type CHECK (element_type IN (
        'PACKAGE',
        'CLASSIFIER',
        'PROPERTY',
        'OPERATION',
        'PARAMETER',
        'ASSOCIATION',
        'GENERALIZATION',
        'REALIZATION',
        'INTERACTION',
        'LIFELINE',
        'OCCURRENCE',
        'MESSAGE'
    )),
    CONSTRAINT fk_uml_elements_model FOREIGN KEY (model_id)
        REFERENCES uml_models (id) ON DELETE RESTRICT
);

CREATE INDEX idx_uml_elements_model ON uml_elements (model_id);
CREATE INDEX idx_uml_elements_model_type ON uml_elements (model_id, element_type);
CREATE UNIQUE INDEX uq_uml_elements_model_external_xmi_id
    ON uml_elements (model_id, external_xmi_id)
    WHERE external_xmi_id IS NOT NULL;

CREATE TABLE uml_packages (
    element_id UUID NOT NULL,
    parent_package_id UUID NULL,
    CONSTRAINT pk_uml_packages PRIMARY KEY (element_id),
    CONSTRAINT fk_uml_packages_element FOREIGN KEY (element_id)
        REFERENCES uml_elements (id) ON DELETE RESTRICT,
    CONSTRAINT fk_uml_packages_parent FOREIGN KEY (parent_package_id)
        REFERENCES uml_packages (element_id) ON DELETE RESTRICT
);

CREATE TABLE uml_classifiers (
    element_id UUID NOT NULL,
    package_id UUID NULL,
    kind VARCHAR(20) NOT NULL,
    visibility VARCHAR(20) NOT NULL DEFAULT 'PUBLIC',
    is_abstract BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_uml_classifiers PRIMARY KEY (element_id),
    CONSTRAINT ck_uml_classifiers_kind CHECK (kind IN ('CLASS', 'INTERFACE')),
    CONSTRAINT ck_uml_classifiers_visibility CHECK (
        visibility IN ('PUBLIC', 'PRIVATE', 'PROTECTED', 'PACKAGE')
    ),
    CONSTRAINT ck_uml_classifiers_interface_not_active CHECK (
        kind <> 'INTERFACE' OR is_active = FALSE
    ),
    CONSTRAINT fk_uml_classifiers_element FOREIGN KEY (element_id)
        REFERENCES uml_elements (id) ON DELETE RESTRICT,
    CONSTRAINT fk_uml_classifiers_package FOREIGN KEY (package_id)
        REFERENCES uml_packages (element_id) ON DELETE RESTRICT
);

CREATE INDEX idx_uml_classifiers_package ON uml_classifiers (package_id);
