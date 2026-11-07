-- Migration V16: Seed production and testing users
-- Password for all seeded users: admin1234
-- Hash: $2a$10$zSlfnqZktD/7p2BhDf0eIOvYpU8tMogT3..94X52IXizYwjWsK3US

INSERT INTO users (id, email, password_hash, display_name, system_role, enabled, created_at, updated_at)
VALUES
    ('c1111111-1111-1111-1111-111111111101', 'demo@casegen.com', '$2a$10$zSlfnqZktD/7p2BhDf0eIOvYpU8tMogT3..94X52IXizYwjWsK3US', 'Usuario Demo', 'USER', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('c1111111-1111-1111-1111-111111111102', 'arquitecto@casegen.com', '$2a$10$zSlfnqZktD/7p2BhDf0eIOvYpU8tMogT3..94X52IXizYwjWsK3US', 'Arq. Carlos Mendoza', 'USER', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('c1111111-1111-1111-1111-111111111103', 'analista@casegen.com', '$2a$10$zSlfnqZktD/7p2BhDf0eIOvYpU8tMogT3..94X52IXizYwjWsK3US', 'Lic. Sofia Gomez', 'USER', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('c1111111-1111-1111-1111-111111111104', 'disenador@casegen.com', '$2a$10$zSlfnqZktD/7p2BhDf0eIOvYpU8tMogT3..94X52IXizYwjWsK3US', 'Ing. Andres Silva', 'USER', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('c1111111-1111-1111-1111-111111111105', 'dev.backend@casegen.com', '$2a$10$zSlfnqZktD/7p2BhDf0eIOvYpU8tMogT3..94X52IXizYwjWsK3US', 'Ing. Mateo Ortiz', 'USER', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('c1111111-1111-1111-1111-111111111106', 'dev.frontend@casegen.com', '$2a$10$zSlfnqZktD/7p2BhDf0eIOvYpU8tMogT3..94X52IXizYwjWsK3US', 'Ing. Lucia Fernandez', 'USER', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('c1111111-1111-1111-1111-111111111107', 'qa.lead@casegen.com', '$2a$10$zSlfnqZktD/7p2BhDf0eIOvYpU8tMogT3..94X52IXizYwjWsK3US', 'Ing. Diego Morales', 'USER', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('c1111111-1111-1111-1111-111111111108', 'dba@casegen.com', '$2a$10$zSlfnqZktD/7p2BhDf0eIOvYpU8tMogT3..94X52IXizYwjWsK3US', 'Ing. Mariana Rios', 'USER', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('c1111111-1111-1111-1111-111111111109', 'revisor@casegen.com', '$2a$10$zSlfnqZktD/7p2BhDf0eIOvYpU8tMogT3..94X52IXizYwjWsK3US', 'Dr. Gabriel Castillo', 'USER', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('c1111111-1111-1111-1111-111111111110', 'colaborador@casegen.com', '$2a$10$zSlfnqZktD/7p2BhDf0eIOvYpU8tMogT3..94X52IXizYwjWsK3US', 'Ing. Elena Vargas', 'USER', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (lower(email)) DO UPDATE
SET display_name = EXCLUDED.display_name,
    password_hash = EXCLUDED.password_hash,
    enabled = true,
    updated_at = CURRENT_TIMESTAMP;
