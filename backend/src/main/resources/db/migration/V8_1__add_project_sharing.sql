ALTER TABLE projects ADD COLUMN visibility VARCHAR(20) NOT NULL DEFAULT 'PRIVATE';
ALTER TABLE projects ADD CONSTRAINT ck_projects_visibility CHECK (visibility IN ('PRIVATE','PUBLIC'));
ALTER TABLE project_invitations DROP CONSTRAINT ck_project_invitations_status;
ALTER TABLE project_invitations ADD CONSTRAINT ck_project_invitations_status
 CHECK (status IN ('PENDING','ACCEPTED','EXPIRED','REVOKED','REJECTED'));
CREATE INDEX idx_project_invitations_recipient ON project_invitations(lower(email),status,expires_at);
