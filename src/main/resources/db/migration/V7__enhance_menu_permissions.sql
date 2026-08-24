-- FR-010: Enhanced Menu Permissions (T17)
-- Add missing indexes for performance optimization

CREATE INDEX IF NOT EXISTS idx_rmp_role ON role_menu_permissions(role_id);
CREATE INDEX IF NOT EXISTS idx_rmp_menu ON role_menu_permissions(menu_id);
CREATE INDEX IF NOT EXISTS idx_rmp_role_granted ON role_menu_permissions(role_id, is_granted) WHERE is_granted = TRUE;

CREATE INDEX IF NOT EXISTS idx_umo_user ON user_menu_overrides(user_id);
CREATE INDEX IF NOT EXISTS idx_umo_user_menu ON user_menu_overrides(user_id, menu_id);

-- Add permission_code column to menu_items for BUTTON type (FR-010 button-level)
ALTER TABLE menu_items ADD COLUMN IF NOT EXISTS permission_code VARCHAR(50);
