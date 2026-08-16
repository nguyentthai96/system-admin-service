-- =====================================================
-- FR-012: API Usage Tracking & Rate Limit Audit
-- =====================================================

-- API usage tracking per key per day
CREATE TABLE api_usage_daily (
    id              BIGINT PRIMARY KEY,
    api_key_id      BIGINT NOT NULL,
    usage_date      DATE NOT NULL,
    request_count   BIGINT NOT NULL DEFAULT 0,
    error_count     BIGINT NOT NULL DEFAULT 0,
    avg_latency_ms  INT DEFAULT 0,
    quota_used      BIGINT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(api_key_id, usage_date)
);

CREATE INDEX idx_api_usage_key_date ON api_usage_daily(api_key_id, usage_date);

-- Rate limit audit log
CREATE TABLE rate_limit_audit (
    id              BIGINT PRIMARY KEY,
    api_key_id      BIGINT NOT NULL,
    endpoint        VARCHAR(255),
    client_ip       VARCHAR(45),
    rejected_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    limit_type      VARCHAR(20) NOT NULL CHECK (limit_type IN ('PER_SECOND', 'PER_MINUTE', 'PER_DAY', 'QUOTA')),
    current_count   BIGINT,
    limit_value     BIGINT
);

CREATE INDEX idx_rate_limit_audit_key ON rate_limit_audit(api_key_id);
CREATE INDEX idx_rate_limit_audit_time ON rate_limit_audit(rejected_at);
