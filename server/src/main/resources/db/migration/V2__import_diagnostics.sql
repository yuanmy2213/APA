ALTER TABLE risk_finding ADD COLUMN link_method VARCHAR(40);
ALTER TABLE risk_finding ADD COLUMN link_reason VARCHAR(80);
ALTER TABLE risk_finding ADD COLUMN link_detail TEXT;
ALTER TABLE risk_finding ADD COLUMN link_candidates INTEGER DEFAULT 0;
ALTER TABLE import_job ADD COLUMN phase VARCHAR(40);
ALTER TABLE import_job ADD COLUMN current_sheet VARCHAR(200);
ALTER TABLE import_job ADD COLUMN processed_rows BIGINT DEFAULT 0;
ALTER TABLE import_job ADD COLUMN completed_sheets INTEGER DEFAULT 0;
