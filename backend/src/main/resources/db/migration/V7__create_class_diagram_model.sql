CREATE TABLE diagrams (
    id UUID NOT NULL,
    model_id UUID NOT NULL,
    owner_package_id UUID NULL,
    name VARCHAR(180) NOT NULL,
    type VARCHAR(20) NOT NULL,
    revision BIGINT NOT NULL DEFAULT 0,
    lock_version BIGINT NOT NULL DEFAULT 0,
    created_by UUID NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_diagrams PRIMARY KEY (id),
    CONSTRAINT uq_diagrams_model_name UNIQUE (model_id, name),
    CONSTRAINT ck_diagrams_type CHECK (type IN ('CLASS')),
    CONSTRAINT ck_diagrams_revision CHECK (revision >= 0),
    CONSTRAINT ck_diagrams_lock_version CHECK (lock_version >= 0),
    CONSTRAINT fk_diagrams_model FOREIGN KEY (model_id)
        REFERENCES uml_models (id) ON DELETE CASCADE,
    CONSTRAINT fk_diagrams_owner_package FOREIGN KEY (owner_package_id)
        REFERENCES uml_packages (element_id) ON DELETE SET NULL,
    CONSTRAINT fk_diagrams_created_by FOREIGN KEY (created_by)
        REFERENCES users (id) ON DELETE RESTRICT
);

CREATE INDEX idx_diagrams_model ON diagrams (model_id);

CREATE TABLE diagram_nodes (
    id UUID NOT NULL,
    diagram_id UUID NOT NULL,
    uml_element_id UUID NOT NULL,
    x DOUBLE PRECISION NOT NULL,
    y DOUBLE PRECISION NOT NULL,
    width DOUBLE PRECISION NOT NULL,
    height DOUBLE PRECISION NOT NULL,
    z_index INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_diagram_nodes PRIMARY KEY (id),
    CONSTRAINT uq_diagram_nodes_element UNIQUE (diagram_id, uml_element_id),
    CONSTRAINT ck_diagram_nodes_size CHECK (width > 0 AND height > 0),
    CONSTRAINT fk_diagram_nodes_diagram FOREIGN KEY (diagram_id)
        REFERENCES diagrams (id) ON DELETE CASCADE,
    CONSTRAINT fk_diagram_nodes_element FOREIGN KEY (uml_element_id)
        REFERENCES uml_elements (id) ON DELETE CASCADE
);

CREATE INDEX idx_diagram_nodes_diagram ON diagram_nodes (diagram_id);
CREATE INDEX idx_diagram_nodes_element ON diagram_nodes (uml_element_id);

CREATE TABLE diagram_edges (
    id UUID NOT NULL,
    diagram_id UUID NOT NULL,
    uml_element_id UUID NOT NULL,
    source_node_id UUID NOT NULL,
    target_node_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_diagram_edges PRIMARY KEY (id),
    CONSTRAINT uq_diagram_edges_element UNIQUE (diagram_id, uml_element_id),
    CONSTRAINT fk_diagram_edges_diagram FOREIGN KEY (diagram_id)
        REFERENCES diagrams (id) ON DELETE CASCADE,
    CONSTRAINT fk_diagram_edges_element FOREIGN KEY (uml_element_id)
        REFERENCES uml_elements (id) ON DELETE CASCADE,
    CONSTRAINT fk_diagram_edges_source FOREIGN KEY (source_node_id)
        REFERENCES diagram_nodes (id) ON DELETE CASCADE,
    CONSTRAINT fk_diagram_edges_target FOREIGN KEY (target_node_id)
        REFERENCES diagram_nodes (id) ON DELETE CASCADE
);

CREATE INDEX idx_diagram_edges_diagram ON diagram_edges (diagram_id);
CREATE INDEX idx_diagram_edges_element ON diagram_edges (uml_element_id);
CREATE INDEX idx_diagram_edges_source ON diagram_edges (source_node_id);
CREATE INDEX idx_diagram_edges_target ON diagram_edges (target_node_id);
