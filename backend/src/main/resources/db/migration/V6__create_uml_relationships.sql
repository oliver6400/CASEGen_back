ALTER TABLE uml_properties
    ALTER COLUMN owner_classifier_id DROP NOT NULL;

CREATE TABLE uml_associations (
    element_id UUID NOT NULL,
    package_id UUID NULL,
    CONSTRAINT pk_uml_associations PRIMARY KEY (element_id),
    CONSTRAINT fk_uml_associations_element FOREIGN KEY (element_id)
        REFERENCES uml_elements (id) ON DELETE RESTRICT,
    CONSTRAINT fk_uml_associations_package FOREIGN KEY (package_id)
        REFERENCES uml_packages (element_id) ON DELETE RESTRICT
);

CREATE INDEX idx_uml_associations_package ON uml_associations (package_id);

CREATE TABLE uml_association_ends (
    property_id UUID NOT NULL,
    association_id UUID NOT NULL,
    navigable BOOLEAN NOT NULL DEFAULT FALSE,
    aggregation VARCHAR(20) NOT NULL DEFAULT 'NONE',
    ownership VARCHAR(20) NOT NULL DEFAULT 'ASSOCIATION',
    member_order SMALLINT NOT NULL,
    CONSTRAINT pk_uml_association_ends PRIMARY KEY (property_id),
    CONSTRAINT uq_uml_association_ends_order UNIQUE (association_id, member_order),
    CONSTRAINT ck_uml_association_ends_aggregation CHECK (
        aggregation IN ('NONE', 'SHARED', 'COMPOSITE')
    ),
    CONSTRAINT ck_uml_association_ends_ownership CHECK (
        ownership IN ('ASSOCIATION', 'CLASSIFIER')
    ),
    CONSTRAINT ck_uml_association_ends_member_order CHECK (member_order IN (1, 2)),
    CONSTRAINT fk_uml_association_ends_property FOREIGN KEY (property_id)
        REFERENCES uml_properties (element_id) ON DELETE RESTRICT,
    CONSTRAINT fk_uml_association_ends_association FOREIGN KEY (association_id)
        REFERENCES uml_associations (element_id) ON DELETE RESTRICT
);

CREATE INDEX idx_uml_association_ends_association
    ON uml_association_ends (association_id);

CREATE TABLE uml_generalizations (
    element_id UUID NOT NULL,
    specific_classifier_id UUID NOT NULL,
    general_classifier_id UUID NOT NULL,
    is_substitutable BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_uml_generalizations PRIMARY KEY (element_id),
    CONSTRAINT uq_uml_generalizations_pair UNIQUE (
        specific_classifier_id, general_classifier_id
    ),
    CONSTRAINT ck_uml_generalizations_distinct CHECK (
        specific_classifier_id <> general_classifier_id
    ),
    CONSTRAINT fk_uml_generalizations_element FOREIGN KEY (element_id)
        REFERENCES uml_elements (id) ON DELETE RESTRICT,
    CONSTRAINT fk_uml_generalizations_specific FOREIGN KEY (specific_classifier_id)
        REFERENCES uml_classifiers (element_id) ON DELETE RESTRICT,
    CONSTRAINT fk_uml_generalizations_general FOREIGN KEY (general_classifier_id)
        REFERENCES uml_classifiers (element_id) ON DELETE RESTRICT
);

CREATE INDEX idx_uml_generalizations_specific
    ON uml_generalizations (specific_classifier_id);
CREATE INDEX idx_uml_generalizations_general
    ON uml_generalizations (general_classifier_id);

CREATE TABLE uml_realizations (
    element_id UUID NOT NULL,
    client_classifier_id UUID NOT NULL,
    supplier_classifier_id UUID NOT NULL,
    CONSTRAINT pk_uml_realizations PRIMARY KEY (element_id),
    CONSTRAINT uq_uml_realizations_pair UNIQUE (
        client_classifier_id, supplier_classifier_id
    ),
    CONSTRAINT ck_uml_realizations_distinct CHECK (
        client_classifier_id <> supplier_classifier_id
    ),
    CONSTRAINT fk_uml_realizations_element FOREIGN KEY (element_id)
        REFERENCES uml_elements (id) ON DELETE RESTRICT,
    CONSTRAINT fk_uml_realizations_client FOREIGN KEY (client_classifier_id)
        REFERENCES uml_classifiers (element_id) ON DELETE RESTRICT,
    CONSTRAINT fk_uml_realizations_supplier FOREIGN KEY (supplier_classifier_id)
        REFERENCES uml_classifiers (element_id) ON DELETE RESTRICT
);

CREATE INDEX idx_uml_realizations_client
    ON uml_realizations (client_classifier_id);
CREATE INDEX idx_uml_realizations_supplier
    ON uml_realizations (supplier_classifier_id);
