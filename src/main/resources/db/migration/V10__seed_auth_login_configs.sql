-- Seed password policy configurations
INSERT INTO system_configs (id, domain_id, config_key, config_value, value_type, version, active, created_at, created_by, updated_at, updated_by)
VALUES
(nextval('snowflake_seq'), 0, 'password.min_length', '8', 'NUMBER', 1, true, CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, 'system'),
(nextval('snowflake_seq'), 0, 'password.max_length', '128', 'NUMBER', 1, true, CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, 'system'),
(nextval('snowflake_seq'), 0, 'password.require_uppercase', 'true', 'BOOLEAN', 1, true, CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, 'system'),
(nextval('snowflake_seq'), 0, 'password.require_lowercase', 'true', 'BOOLEAN', 1, true, CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, 'system'),
(nextval('snowflake_seq'), 0, 'password.require_digit', 'true', 'BOOLEAN', 1, true, CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, 'system'),
(nextval('snowflake_seq'), 0, 'password.require_special', 'false', 'BOOLEAN', 1, true, CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, 'system'),
(nextval('snowflake_seq'), 0, 'password.min_character_types', '3', 'NUMBER', 1, true, CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, 'system'),
(nextval('snowflake_seq'), 0, 'password.history_count', '5', 'NUMBER', 1, true, CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, 'system'),
(nextval('snowflake_seq'), 0, 'password.max_age_days', '90', 'NUMBER', 1, true, CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, 'system'),
(nextval('snowflake_seq'), 0, 'password.lockout_threshold', '5', 'NUMBER', 1, true, CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, 'system'),
(nextval('snowflake_seq'), 0, 'password.lockout_duration_minutes', '15', 'NUMBER', 1, true, CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, 'system')
ON CONFLICT DO NOTHING;
