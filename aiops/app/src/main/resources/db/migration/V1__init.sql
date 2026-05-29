-- AIOps state schema
-- Flyway runs this once when postgres-aiops container is fresh

-- Container logs (thay thế Elasticsearch)
CREATE TABLE IF NOT EXISTS container_logs (
    id           BIGSERIAL PRIMARY KEY,
    container    VARCHAR(100) NOT NULL,
    log_level    VARCHAR(10),               -- INFO, WARN, ERROR
    message      TEXT         NOT NULL,
    logged_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_logs_container_time ON container_logs(container, logged_at DESC);
CREATE INDEX idx_logs_level          ON container_logs(log_level, logged_at DESC);

CREATE TABLE IF NOT EXISTS alerts (
    id          BIGSERIAL PRIMARY KEY,
    container   VARCHAR(100) NOT NULL,
    type        VARCHAR(50)  NOT NULL,   -- ERROR_RATE_SPIKE, HIGH_LATENCY, SERVICE_DOWN
    severity    VARCHAR(10)  NOT NULL,   -- P1, P2, P3, P4
    message     TEXT         NOT NULL,
    detected_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    resolved_at TIMESTAMPTZ,
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS incidents (
    id           BIGSERIAL PRIMARY KEY,
    alert_id     BIGINT REFERENCES alerts(id),
    analysis     TEXT,                  -- full AI response text
    root_cause   TEXT,
    remediation  TEXT,
    analyzed_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_alerts_container   ON alerts(container);
CREATE INDEX idx_alerts_active      ON alerts(is_active, detected_at DESC);
CREATE INDEX idx_incidents_alert    ON incidents(alert_id);
