ALTER TABLE diagram_edges
    ADD COLUMN source_side VARCHAR(10) NOT NULL DEFAULT 'RIGHT',
    ADD COLUMN target_side VARCHAR(10) NOT NULL DEFAULT 'LEFT',
    ADD COLUMN source_anchor_index INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN target_anchor_index INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN waypoints_json JSONB NOT NULL DEFAULT '[]'::jsonb;

ALTER TABLE diagram_edges
    ADD CONSTRAINT ck_diagram_edges_source_side
        CHECK (source_side IN ('TOP', 'RIGHT', 'BOTTOM', 'LEFT')),
    ADD CONSTRAINT ck_diagram_edges_target_side
        CHECK (target_side IN ('TOP', 'RIGHT', 'BOTTOM', 'LEFT')),
    ADD CONSTRAINT ck_diagram_edges_source_anchor_index
        CHECK (source_anchor_index >= 0),
    ADD CONSTRAINT ck_diagram_edges_target_anchor_index
        CHECK (target_anchor_index >= 0),
    ADD CONSTRAINT ck_diagram_edges_waypoints_array
        CHECK (jsonb_typeof(waypoints_json) = 'array');

WITH ranked_source AS (
    SELECT id, ROW_NUMBER() OVER (
        PARTITION BY source_node_id ORDER BY created_at, id
    ) - 1 AS anchor_index
    FROM diagram_edges
)
UPDATE diagram_edges edge
SET source_anchor_index = ranked_source.anchor_index
FROM ranked_source
WHERE edge.id = ranked_source.id;

WITH ranked_target AS (
    SELECT id, ROW_NUMBER() OVER (
        PARTITION BY target_node_id ORDER BY created_at, id
    ) - 1 AS anchor_index
    FROM diagram_edges
)
UPDATE diagram_edges edge
SET target_anchor_index = ranked_target.anchor_index
FROM ranked_target
WHERE edge.id = ranked_target.id;
