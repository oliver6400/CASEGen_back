CREATE TABLE logical_models (
 id UUID PRIMARY KEY,
 model_version_id UUID NOT NULL REFERENCES model_versions(id),
 generation_profile_id UUID NOT NULL REFERENCES generation_profiles(id) ON DELETE RESTRICT,
 profile_snapshot JSONB NOT NULL CHECK (jsonb_typeof(profile_snapshot) = 'object'),
 status VARCHAR(20) NOT NULL CHECK (status IN ('VALID','INVALID')),
 checksum VARCHAR(64) NOT NULL,
 issues_json JSONB NOT NULL DEFAULT '[]'::jsonb CHECK (jsonb_typeof(issues_json) = 'array'),
 created_by UUID REFERENCES users(id),
 created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE INDEX idx_logical_models_version ON logical_models(model_version_id);
CREATE INDEX idx_logical_models_profile ON logical_models(generation_profile_id);
-- Source UML identifiers deliberately have no live FK: immutable source versions
-- and their derived relational models survive deletion in the editable model.
CREATE TABLE logical_tables (
 id UUID PRIMARY KEY,
 logical_model_id UUID NOT NULL REFERENCES logical_models(id) ON DELETE CASCADE,
 source_classifier_id UUID,
 source_association_id UUID,
 name VARCHAR(63) NOT NULL,
 UNIQUE(logical_model_id,name)
);
CREATE TABLE logical_columns (
 id UUID PRIMARY KEY,
 table_id UUID NOT NULL REFERENCES logical_tables(id) ON DELETE CASCADE,
 source_property_id UUID,
 name VARCHAR(63) NOT NULL,
 sql_type VARCHAR(100) NOT NULL,
 nullable BOOLEAN NOT NULL,
 ordinal_position INT NOT NULL CHECK (ordinal_position > 0),
 UNIQUE(table_id,name), UNIQUE(table_id,ordinal_position), UNIQUE(table_id,id)
);
CREATE TABLE logical_primary_keys (
 id UUID PRIMARY KEY,
 table_id UUID NOT NULL UNIQUE REFERENCES logical_tables(id) ON DELETE CASCADE,
 name VARCHAR(63) NOT NULL,
 source_element_id UUID,
 UNIQUE(table_id,id)
);
CREATE TABLE logical_primary_key_columns (
 primary_key_id UUID NOT NULL,
 table_id UUID NOT NULL,
 column_id UUID NOT NULL,
 position INT NOT NULL CHECK (position > 0),
 PRIMARY KEY(primary_key_id,position), UNIQUE(primary_key_id,column_id),
 FOREIGN KEY(table_id,primary_key_id) REFERENCES logical_primary_keys(table_id,id) ON DELETE CASCADE,
 FOREIGN KEY(table_id,column_id) REFERENCES logical_columns(table_id,id) ON DELETE CASCADE
);
CREATE TABLE logical_foreign_keys (
 id UUID PRIMARY KEY,
 table_id UUID NOT NULL REFERENCES logical_tables(id) ON DELETE CASCADE,
 referenced_table_id UUID NOT NULL REFERENCES logical_tables(id) ON DELETE CASCADE,
 name VARCHAR(63) NOT NULL,
 on_delete VARCHAR(20) NOT NULL CHECK (on_delete IN ('NO ACTION','CASCADE')),
 source_element_id UUID,
 UNIQUE(table_id,name), UNIQUE(table_id,referenced_table_id,id)
);
CREATE TABLE logical_foreign_key_columns (
 foreign_key_id UUID NOT NULL,
 table_id UUID NOT NULL,
 referenced_table_id UUID NOT NULL,
 column_id UUID NOT NULL,
 referenced_column_id UUID NOT NULL,
 position INT NOT NULL CHECK (position > 0),
 PRIMARY KEY(foreign_key_id,position), UNIQUE(foreign_key_id,column_id),
 FOREIGN KEY(table_id,referenced_table_id,foreign_key_id) REFERENCES logical_foreign_keys(table_id,referenced_table_id,id) ON DELETE CASCADE,
 FOREIGN KEY(table_id,column_id) REFERENCES logical_columns(table_id,id) ON DELETE CASCADE,
 FOREIGN KEY(referenced_table_id,referenced_column_id) REFERENCES logical_columns(table_id,id) ON DELETE CASCADE
);
CREATE TABLE logical_unique_constraints (
 id UUID PRIMARY KEY, table_id UUID NOT NULL REFERENCES logical_tables(id) ON DELETE CASCADE,
 name VARCHAR(63) NOT NULL, source_element_id UUID,
 UNIQUE(table_id,name), UNIQUE(table_id,id)
);
CREATE TABLE logical_unique_constraint_columns (
 unique_constraint_id UUID NOT NULL, table_id UUID NOT NULL, column_id UUID NOT NULL,
 position INT NOT NULL CHECK (position > 0),
 PRIMARY KEY(unique_constraint_id,position), UNIQUE(unique_constraint_id,column_id),
 FOREIGN KEY(table_id,unique_constraint_id) REFERENCES logical_unique_constraints(table_id,id) ON DELETE CASCADE,
 FOREIGN KEY(table_id,column_id) REFERENCES logical_columns(table_id,id) ON DELETE CASCADE
);
CREATE TABLE logical_indexes (
 id UUID PRIMARY KEY, table_id UUID NOT NULL REFERENCES logical_tables(id) ON DELETE CASCADE,
 name VARCHAR(63) NOT NULL, is_unique BOOLEAN NOT NULL DEFAULT false, source_element_id UUID,
 UNIQUE(table_id,name), UNIQUE(table_id,id)
);
CREATE TABLE logical_index_columns (
 index_id UUID NOT NULL, table_id UUID NOT NULL, column_id UUID NOT NULL,
 position INT NOT NULL CHECK (position > 0),
 PRIMARY KEY(index_id,position), UNIQUE(index_id,column_id),
 FOREIGN KEY(table_id,index_id) REFERENCES logical_indexes(table_id,id) ON DELETE CASCADE,
 FOREIGN KEY(table_id,column_id) REFERENCES logical_columns(table_id,id) ON DELETE CASCADE
);
CREATE TABLE logical_check_constraints (
 id UUID PRIMARY KEY, table_id UUID NOT NULL REFERENCES logical_tables(id) ON DELETE CASCADE,
 name VARCHAR(63) NOT NULL, expression TEXT NOT NULL, source_element_id UUID,
 UNIQUE(table_id,name)
);
