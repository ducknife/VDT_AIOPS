ALTER TABLE container_logs RENAME TO service_logs;
ALTER INDEX idx_logs_container_time RENAME TO idx_service_logs_time;
ALTER INDEX idx_logs_level RENAME TO idx_service_logs_level;

ALTER TABLE alerts RENAME COLUMN container TO service;
ALTER INDEX idx_alerts_container RENAME TO idx_alerts_service;