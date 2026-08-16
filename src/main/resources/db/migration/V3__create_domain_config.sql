-- ============================================
-- V3: Domain/Tenant Configuration for FR-016
-- Service: system-admin-service
-- ============================================

CREATE TABLE IF NOT EXISTS domain_configs (
    id                      BIGINT PRIMARY KEY,
    domain_id               BIGINT       NOT NULL UNIQUE,
    branding_json           TEXT,
    login_page_config_json  TEXT,
    password_policy_json    TEXT,
    mfa_policy_json         TEXT,
    session_policy_json     TEXT,
    allowed_ip_ranges_json  TEXT,
    max_users               INT          NOT NULL DEFAULT 1000,
    max_api_partners        INT          NOT NULL DEFAULT 50,
    is_active               BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at              BIGINT       NOT NULL,
    created_by              VARCHAR(100),
    updated_at              BIGINT,
    updated_by              VARCHAR(100)
);

CREATE INDEX idx_domain_configs_domain_id ON domain_configs(domain_id);

COMMENT ON TABLE domain_configs IS 'Per-domain tenant configuration (FR-016)';
COMMENT ON COLUMN domain_configs.branding_json IS 'Logo, colors, theme per tenant';
COMMENT ON COLUMN domain_configs.session_policy_json IS 'Max concurrent sessions, timeout settings';
