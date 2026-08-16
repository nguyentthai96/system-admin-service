-- ============================================
-- V2: API Partner Management tables for FR-012
-- Service: system-admin-service
-- ============================================

-- Subscription plans (rate limit tiers)
CREATE TABLE IF NOT EXISTS subscription_plans (
    id                      BIGINT PRIMARY KEY,
    code                    VARCHAR(50)  NOT NULL UNIQUE,
    name                    VARCHAR(200) NOT NULL,
    description             VARCHAR(500),
    max_requests_per_day    BIGINT       NOT NULL DEFAULT 10000,
    max_requests_per_month  BIGINT       NOT NULL DEFAULT 300000,
    rate_limit_per_second   INT          NOT NULL DEFAULT 10,
    allowed_apis_json       TEXT,
    price_monthly           DECIMAL(12,2),
    status                  VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    is_active               BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at              BIGINT       NOT NULL,
    created_by              VARCHAR(100),
    updated_at              BIGINT,
    updated_by              VARCHAR(100)
);

-- API Partners
CREATE TABLE IF NOT EXISTS api_partners (
    id                      BIGINT PRIMARY KEY,
    domain_id               BIGINT       NOT NULL,
    partner_name            VARCHAR(200) NOT NULL,
    partner_code            VARCHAR(50)  NOT NULL UNIQUE,
    contact_email           VARCHAR(255),
    contact_phone           VARCHAR(50),
    description             VARCHAR(500),
    subscription_plan_id    BIGINT REFERENCES subscription_plans(id),
    status                  VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    is_active               BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at              BIGINT       NOT NULL,
    created_by              VARCHAR(100),
    updated_at              BIGINT,
    updated_by              VARCHAR(100)
);

-- API Keys (BR-API-01: key shown only once, stored as hash)
CREATE TABLE IF NOT EXISTS api_keys (
    id                      BIGINT PRIMARY KEY,
    partner_id              BIGINT       NOT NULL REFERENCES api_partners(id),
    key_prefix              VARCHAR(20)  NOT NULL,
    key_hash                VARCHAR(128) NOT NULL UNIQUE,
    name                    VARCHAR(200),
    scopes_json             TEXT,
    rate_limit_per_second   INT          NOT NULL DEFAULT 10,
    rate_limit_per_minute   INT          NOT NULL DEFAULT 600,
    rate_limit_per_day      BIGINT       NOT NULL DEFAULT 10000,
    quota_monthly           BIGINT       NOT NULL DEFAULT 300000,
    ip_whitelist_json       TEXT,
    expires_at              BIGINT,
    last_used_at            BIGINT,
    status                  VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    is_active               BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at              BIGINT       NOT NULL,
    created_by              VARCHAR(100),
    updated_at              BIGINT,
    updated_by              VARCHAR(100)
);

-- API Usage Logs (aggregation source for dashboard)
CREATE TABLE IF NOT EXISTS api_usage_logs (
    id                      BIGINT PRIMARY KEY,
    partner_id              BIGINT       NOT NULL REFERENCES api_partners(id),
    api_key_id              BIGINT       NOT NULL REFERENCES api_keys(id),
    endpoint                VARCHAR(500) NOT NULL,
    method                  VARCHAR(10)  NOT NULL,
    status_code             INT          NOT NULL,
    response_time_ms        INT,
    ip_address              VARCHAR(45),
    request_at              BIGINT       NOT NULL,
    is_active               BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at              BIGINT       NOT NULL,
    created_by              VARCHAR(100),
    updated_at              BIGINT,
    updated_by              VARCHAR(100)
);

-- Indexes
CREATE INDEX idx_api_partners_domain_id ON api_partners(domain_id);
CREATE INDEX idx_api_partners_code ON api_partners(partner_code);
CREATE INDEX idx_api_keys_partner_id ON api_keys(partner_id);
CREATE INDEX idx_api_keys_prefix ON api_keys(key_prefix);
CREATE INDEX idx_api_keys_hash ON api_keys(key_hash);
CREATE INDEX idx_api_usage_logs_partner_id ON api_usage_logs(partner_id);
CREATE INDEX idx_api_usage_logs_request_at ON api_usage_logs(request_at);

COMMENT ON TABLE api_keys IS 'API keys with one-way hash storage (BR-API-01)';
COMMENT ON COLUMN api_keys.key_prefix IS 'ntt_pk_ (production) or ntt_sk_ (sandbox) — BR-API-04';
