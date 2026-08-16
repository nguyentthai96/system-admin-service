-- =====================================================
-- FR-016: Domain Config Versioning & History
-- =====================================================

CREATE TABLE domain_config_history (
    id              BIGINT PRIMARY KEY,
    domain_id       BIGINT NOT NULL,
    config_snapshot JSONB NOT NULL,
    changed_by      VARCHAR(100),
    changed_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    change_reason   TEXT,
    version         INT NOT NULL DEFAULT 1
);

CREATE INDEX idx_domain_config_history_domain ON domain_config_history(domain_id);
CREATE INDEX idx_domain_config_history_version ON domain_config_history(domain_id, version DESC);
