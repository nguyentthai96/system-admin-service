-- V6: Config versioning with history tracking, workflow delegation fields (FR-014, FR-013)

-- Config history table
CREATE TABLE IF NOT EXISTS domain_config_history (
    id              BIGINT PRIMARY KEY,
    domain_id       BIGINT NOT NULL,
    config_key      VARCHAR(255) NOT NULL,
    old_value       TEXT,
    new_value       TEXT,
    version         INT NOT NULL DEFAULT 1,
    changed_by      VARCHAR(100),
    changed_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_config_history_domain_key ON domain_config_history (domain_id, config_key);
CREATE INDEX IF NOT EXISTS idx_config_history_changed_at ON domain_config_history (changed_at);

-- Add version column to system_configs if not exists
ALTER TABLE system_configs ADD COLUMN IF NOT EXISTS version INT DEFAULT 1;

-- Workflow step delegation fields (FR-013)
ALTER TABLE workflow_steps ADD COLUMN IF NOT EXISTS delegated_from BIGINT;
ALTER TABLE workflow_steps ADD COLUMN IF NOT EXISTS delegation_reason VARCHAR(500);
ALTER TABLE workflow_steps ADD COLUMN IF NOT EXISTS condition_json JSONB;
ALTER TABLE workflow_steps ADD COLUMN IF NOT EXISTS decided_by BIGINT;

-- Immutability constraint on audit_logs (FR-015)
-- Prevent UPDATE and DELETE on audit_logs table
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_rules WHERE rulename = 'audit_logs_no_update') THEN
        CREATE RULE audit_logs_no_update AS ON UPDATE TO audit_logs DO INSTEAD NOTHING;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_rules WHERE rulename = 'audit_logs_no_delete') THEN
        CREATE RULE audit_logs_no_delete AS ON DELETE TO audit_logs DO INSTEAD NOTHING;
    END IF;
END $$;
