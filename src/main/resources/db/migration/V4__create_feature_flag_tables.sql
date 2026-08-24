-- V4: Create feature flag and system config tables (FR-014)

CREATE TABLE system_configs (
    id              BIGINT PRIMARY KEY,
    domain_id       BIGINT NOT NULL,
    config_key      VARCHAR(100) NOT NULL,
    config_value    VARCHAR(4000) NOT NULL,
    value_type      VARCHAR(20) NOT NULL DEFAULT 'STRING',
    description     VARCHAR(500),
    version         INTEGER NOT NULL DEFAULT 1,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    created_by      VARCHAR(100),
    updated_by      VARCHAR(100),
    CONSTRAINT uq_system_configs UNIQUE (domain_id, config_key)
);

CREATE TABLE feature_flags (
    id              BIGINT PRIMARY KEY,
    domain_id       BIGINT NOT NULL,
    flag_key        VARCHAR(100) NOT NULL,
    enabled         BOOLEAN NOT NULL DEFAULT FALSE,
    rollout_pct     INTEGER NOT NULL DEFAULT 0,
    user_segments   JSONB DEFAULT '[]',
    description     VARCHAR(500),
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    created_by      VARCHAR(100),
    updated_by      VARCHAR(100),
    CONSTRAINT uq_feature_flags UNIQUE (domain_id, flag_key)
);

CREATE INDEX idx_feature_flags_domain ON feature_flags(domain_id);
CREATE INDEX idx_system_configs_domain ON system_configs(domain_id);
