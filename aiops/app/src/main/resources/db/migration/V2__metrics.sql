CREATE TABLE metric_snapshots (
    id             BIGSERIAL PRIMARY KEY,
    container      VARCHAR(100) NOT NULL,
    scraped_at     TIMESTAMPTZ  NOT NULL,
    cpu_percent    DOUBLE PRECISION,
    mem_percent    DOUBLE PRECISION,
    mem_used_bytes BIGINT,
    request_rate   DOUBLE PRECISION,   -- req/giây
    error_rate     DOUBLE PRECISION,   -- errors/giây
    latency_ms     DOUBLE PRECISION,
    healthy        BOOLEAN
);

CREATE INDEX idx_metrics_container_time ON metric_snapshots(container, scraped_at DESC);
