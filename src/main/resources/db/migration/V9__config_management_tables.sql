-- =====================================================
-- V9: Configuration Management, Milestone Snapshots & Audit
-- Part of: config-management-system (FR-001, FR-008, FR-009)
-- =====================================================

CREATE TABLE IF NOT EXISTS sys_config_milestone (
    id          VARCHAR(64) PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    created_by  VARCHAR(100),
    created_at  TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    status      VARCHAR(30) DEFAULT 'ACTIVE'
);

CREATE TABLE IF NOT EXISTS sys_config_snapshot (
    id              VARCHAR(64) PRIMARY KEY,
    milestone_id    VARCHAR(64) REFERENCES sys_config_milestone(id) ON DELETE CASCADE,
    domain_name     VARCHAR(50) NOT NULL,
    state_payload   JSONB NOT NULL,
    checksum_sha256 VARCHAR(64) NOT NULL,
    record_count    INT NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_snapshot_domain_time ON sys_config_snapshot(domain_name, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_snapshot_milestone ON sys_config_snapshot(milestone_id);
CREATE INDEX IF NOT EXISTS idx_snapshot_payload_gin ON sys_config_snapshot USING GIN (state_payload);

-- Dynamic i18n messages table for system-admin-service (FR-001)
CREATE TABLE IF NOT EXISTS i18n_messages (
    id            BIGSERIAL    PRIMARY KEY,
    code          VARCHAR(128) NOT NULL,
    locale        VARCHAR(10)  NOT NULL,
    message       TEXT         NOT NULL,
    module        VARCHAR(64)  NOT NULL DEFAULT 'common',
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    BIGINT       NOT NULL,
    updated_at    BIGINT       NOT NULL,
    CONSTRAINT uq_i18n_code_locale UNIQUE (code, locale)
);

CREATE INDEX IF NOT EXISTS idx_i18n_messages_code ON i18n_messages (code);
CREATE INDEX IF NOT EXISTS idx_i18n_messages_module ON i18n_messages (module);

COMMENT ON TABLE sys_config_milestone IS 'Configuration milestones grouping multi-domain snapshots (FR-009)';
COMMENT ON TABLE sys_config_snapshot IS 'Point-in-time domain snapshot storage with JSONB payload (FR-008, FR-009)';
COMMENT ON TABLE i18n_messages IS 'Multilingual i18n translation messages managed dynamically (FR-001)';
COMMENT ON COLUMN i18n_messages.code IS 'Message key matching ErrorCodeBase.msgCode (e.g., auth.rate_limited)';
COMMENT ON COLUMN i18n_messages.locale IS 'BCP 47 locale tag (e.g., en, vi)';
COMMENT ON COLUMN i18n_messages.message IS 'MessageFormat template with {0}, {1} placeholders';
COMMENT ON COLUMN i18n_messages.module IS 'Service grouping for bulk operations (e.g., auth, common)';
