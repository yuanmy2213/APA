ALTER TABLE source_point ADD COLUMN permission_combination_id VARCHAR(200);
ALTER TABLE matrix_finding ADD COLUMN permission_combination_id VARCHAR(200);

-- Preserve historical row/storage IDs; supply stable, visible business IDs for old bindings.
UPDATE source_point SET permission_combination_id = 'LEGACY-' || record_key
 WHERE permission_combination_id IS NULL;
UPDATE matrix_finding SET permission_combination_id = (
 SELECT p.permission_combination_id FROM risk_finding f JOIN source_point p
 ON p.id = COALESCE(f.current_source_id, f.audited_source_id)
 WHERE f.id = matrix_finding.finding_id
);
ALTER TABLE source_point ALTER COLUMN permission_combination_id SET NOT NULL;
CREATE UNIQUE INDEX uq_point_combination ON source_point(dataset_id,permission_combination_id);
CREATE INDEX ix_matrix_combination ON matrix_finding(permission_combination_id,finding_id);
CREATE INDEX ix_user_grant ON user_role_finding(authorization_id,finding_id);
CREATE INDEX ix_auth_grant ON source_auth(dataset_id,authorization_id);
CREATE INDEX ix_auth_draft_base ON source_auth(dataset_id,base_row_id);
CREATE INDEX ix_point_draft_base ON source_point(dataset_id,base_row_id);
CREATE INDEX ix_finding_draft_base ON risk_finding(dataset_id,base_finding_id);
CREATE INDEX ix_finding_current ON risk_finding(dataset_id,current_source_id);
CREATE INDEX ix_auth_lineage ON source_auth(base_row_id);
CREATE INDEX ix_point_lineage ON source_point(base_row_id);
CREATE INDEX ix_finding_lineage ON risk_finding(base_finding_id);
CREATE INDEX ix_event_target ON audit_event(product_id,target_id,created_at);
