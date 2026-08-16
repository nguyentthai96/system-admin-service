-- =====================================================
-- FR-010: Department-based Menu Permissions
-- =====================================================

-- Department menu permissions — additional permission layer
CREATE TABLE department_menu_permissions (
    id              BIGINT PRIMARY KEY,
    department_id   BIGINT NOT NULL,
    menu_id         BIGINT NOT NULL,
    permission_code VARCHAR(50) NOT NULL,
    is_granted      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(100),
    UNIQUE(department_id, menu_id, permission_code)
);

CREATE INDEX idx_dept_menu_perm_dept ON department_menu_permissions(department_id);
CREATE INDEX idx_dept_menu_perm_menu ON department_menu_permissions(menu_id);
