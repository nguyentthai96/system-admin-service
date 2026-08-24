-- V5: Role-menu permissions and user menu overrides (FR-010)
-- Supports button-level permission, role-menu assignment CRUD, user override.

-- Role ↔ Menu Item permission mapping
CREATE TABLE IF NOT EXISTS role_menu_permissions (
    id              BIGINT PRIMARY KEY,
    role_id         BIGINT NOT NULL,
    menu_item_id    BIGINT NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by      BIGINT,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_role_menu_permission UNIQUE (role_id, menu_item_id)
);

CREATE INDEX IF NOT EXISTS idx_rmp_role_id ON role_menu_permissions (role_id) WHERE active = TRUE;
CREATE INDEX IF NOT EXISTS idx_rmp_menu_item_id ON role_menu_permissions (menu_item_id) WHERE active = TRUE;

-- User-level menu permission overrides (GRANT/DENY)
-- Override priority: user override > role permission
CREATE TABLE IF NOT EXISTS user_menu_overrides (
    id              BIGINT PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    menu_item_id    BIGINT NOT NULL,
    action          VARCHAR(10) NOT NULL CHECK (action IN ('GRANT', 'DENY')),
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by      BIGINT,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_user_menu_override UNIQUE (user_id, menu_item_id)
);

CREATE INDEX IF NOT EXISTS idx_umo_user_id ON user_menu_overrides (user_id) WHERE active = TRUE;
CREATE INDEX IF NOT EXISTS idx_umo_menu_item_id ON user_menu_overrides (menu_item_id) WHERE active = TRUE;

-- Add permission_code column to menus table for BUTTON-type items
ALTER TABLE menus ADD COLUMN IF NOT EXISTS permission_code VARCHAR(100);
ALTER TABLE menus ADD COLUMN IF NOT EXISTS menu_type VARCHAR(20) DEFAULT 'MENU';

COMMENT ON COLUMN menus.permission_code IS 'Permission code for BUTTON-type menu items (e.g., user:create, user:delete)';
COMMENT ON COLUMN menus.menu_type IS 'Menu item type: DIRECTORY, MENU, BUTTON, API';

-- API keys table for API Partner key lifecycle (FR-012)
CREATE TABLE IF NOT EXISTS api_keys (
    id              BIGINT PRIMARY KEY,
    partner_id      BIGINT NOT NULL,
    key_hash        VARCHAR(64) NOT NULL UNIQUE,
    key_type        VARCHAR(10) NOT NULL DEFAULT 'sk',
    key_prefix      VARCHAR(10) NOT NULL DEFAULT 'ntt_sk_',
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    revoked         BOOLEAN NOT NULL DEFAULT FALSE,
    revoked_at      TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_api_keys_partner ON api_keys (partner_id) WHERE active = TRUE AND revoked = FALSE;
CREATE INDEX IF NOT EXISTS idx_api_keys_hash ON api_keys (key_hash);

-- Rate limit columns on api_partners (if not already added)
ALTER TABLE api_partners ADD COLUMN IF NOT EXISTS rate_limit_requests_per_minute INT DEFAULT 100;
ALTER TABLE api_partners ADD COLUMN IF NOT EXISTS rate_limit_burst INT DEFAULT 20;
