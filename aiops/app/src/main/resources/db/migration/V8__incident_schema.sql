-- V8: Incident schema.

-- 1) alerts: tro ve incident cua no
ALTER TABLE alerts ADD COLUMN IF NOT EXISTS incident_id BIGINT REFERENCES incidents(id);
CREATE INDEX IF NOT EXISTS idx_alerts_incident ON alerts(incident_id);

-- 2) incidents: bo FK sai phia + cot legacy da bi thay the
ALTER TABLE incidents DROP COLUMN IF EXISTS alert_id;
ALTER TABLE incidents DROP COLUMN IF EXISTS analysis;
ALTER TABLE incidents DROP COLUMN IF EXISTS remediation;

-- 3) incidents: enrich (giu root_cause, severity, analyzed_at tu V1/V6)
ALTER TABLE incidents ADD COLUMN IF NOT EXISTS service             VARCHAR(100);
ALTER TABLE incidents ADD COLUMN IF NOT EXISTS title               VARCHAR(255);
ALTER TABLE incidents ADD COLUMN IF NOT EXISTS summary             TEXT;
ALTER TABLE incidents ADD COLUMN IF NOT EXISTS investigation_ms    BIGINT;
ALTER TABLE incidents ADD COLUMN IF NOT EXISTS validated_findings  JSONB;
ALTER TABLE incidents ADD COLUMN IF NOT EXISTS hypotheses          JSONB;
ALTER TABLE incidents ADD COLUMN IF NOT EXISTS recommended_actions JSONB;
ALTER TABLE incidents ADD COLUMN IF NOT EXISTS cited_evidence      JSONB;
