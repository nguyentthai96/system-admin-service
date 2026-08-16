-- ============================================
-- V1: Menu Permission tables for FR-010
-- Service: system-admin-service
-- ============================================

-- Menu items tree structure
CREATE TABLE IF NOT EXISTS menu_items (
    id              BIGINT PRIMARY KEY,
    parent_id       BIGINT REFERENCES menu_items(id),
    domain_id       BIGINT       NOT NULL,
    code            VARCHAR(100) NOT NULL,
    name            VARCHAR(200) NOT NULL,
    icon            VARCHAR(100),
    path            VARCHAR(500),
    route_name      VARCHAR(200),
    component       VARCHAR(500),
    sort_order      INT          NOT NULL DEFAULT 0,
    level           INT          NOT NULL DEFAULT 0,
    menu_type       VARCHAR(20)  NOT NULL DEFAULT 'MENU',
    is_visible      BOOLEAN      NOT NULL DEFAULT TRUE,
    is_cacheable    BOOLEAN      NOT NULL DEFAULT FALSE,
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    metadata_json   TEXT,
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      BIGINT       NOT NULL,
    created_by      VARCHAR(100),
    updated_at      BIGINT,
    updated_by      VARCHAR(100)
);

-- Menu permissions (actions on menu items)
CREATE TABLE IF NOT EXISTS menu_permissions (
    id              BIGINT PRIMARY KEY,
    menu_id         BIGINT       NOT NULL REFERENCES menu_items(id),
    permission_code VARCHAR(50)  NOT NULL,
    name            VARCHAR(200) NOT NULL,
    description     VARCHAR(500),
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      BIGINT       NOT NULL,
    created_by      VARCHAR(100),
    updated_at      BIGINT,
    updated_by      VARCHAR(100),
    UNIQUE(menu_id, permission_code)
);

-- Role → Menu → Permission assignments
CREATE TABLE IF NOT EXISTS role_menu_permissions (
    id              BIGINT PRIMARY KEY,
    role_id         BIGINT       NOT NULL,
    menu_id         BIGINT       NOT NULL REFERENCES menu_items(id),
    permission_code VARCHAR(50)  NOT NULL,
    is_granted      BOOLEAN      NOT NULL DEFAULT TRUE,
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      BIGINT       NOT NULL,
    created_by      VARCHAR(100),
    updated_at      BIGINT,
    updated_by      VARCHAR(100),
    UNIQUE(role_id, menu_id, permission_code)
);

-- User-level overrides (higher priority than role — BR-MENU-02)
CREATE TABLE IF NOT EXISTS user_menu_overrides (
    id              BIGINT PRIMARY KEY,
    user_id         BIGINT       NOT NULL,
    menu_id         BIGINT       NOT NULL REFERENCES menu_items(id),
    permission_code VARCHAR(50)  NOT NULL,
    is_granted      BOOLEAN      NOT NULL DEFAULT TRUE,
    reason          VARCHAR(500),
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      BIGINT       NOT NULL,
    created_by      VARCHAR(100),
    updated_at      BIGINT,
    updated_by      VARCHAR(100),
    UNIQUE(user_id, menu_id, permission_code)
);

-- Indexes
CREATE INDEX idx_menu_items_parent_id ON menu_items(parent_id);
CREATE INDEX idx_menu_items_domain_id ON menu_items(domain_id);
CREATE INDEX idx_menu_items_code ON menu_items(code);
CREATE INDEX idx_menu_permissions_menu_id ON menu_permissions(menu_id);
CREATE INDEX idx_role_menu_permissions_role_id ON role_menu_permissions(role_id);
CREATE INDEX idx_role_menu_permissions_menu_id ON role_menu_permissions(menu_id);
CREATE INDEX idx_user_menu_overrides_user_id ON user_menu_overrides(user_id);

COMMENT ON TABLE menu_items IS 'Dynamic menu tree structure (FR-010)';
COMMENT ON COLUMN menu_items.menu_type IS 'DIRECTORY, MENU, BUTTON, API';
COMMENT ON TABLE user_menu_overrides IS 'User-level permission overrides, priority > role (BR-MENU-02)';
