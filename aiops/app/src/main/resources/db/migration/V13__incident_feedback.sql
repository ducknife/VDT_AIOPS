-- V13: Human feedback on an incident's diagnosis. 1:1 voi incident (incident_id = PK).
-- CHI LUU de danh gia/hoc sau; KHONG dua tro lai cho agent lam context (store-only).
--   verdict : correct | partial | wrong                  (chan doan dung/mot phan/sai)
--   missed  : mien-taxonomy khi verdict != correct        (chan doan THIEU/SAI cai gi):
--             wrong-root-cause | missed-service | wrong-severity |
--             missed-split | wrong-remediation | other
--   note    : ghi chu tu do (tuy chon)

CREATE TABLE IF NOT EXISTS incident_feedback (
    incident_id BIGINT      PRIMARY KEY REFERENCES incidents(id),
    verdict     VARCHAR(20) NOT NULL,
    missed      VARCHAR(40),
    note        TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
