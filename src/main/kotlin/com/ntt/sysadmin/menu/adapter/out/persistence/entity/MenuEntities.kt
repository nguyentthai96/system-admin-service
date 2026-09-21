package com.ntt.sysadmin.menu.adapter.out.persistence.entity

import com.ntt.basecore.model.id.SnowflakePersistentAuditableEntity
import jakarta.persistence.*

/**
 * Menu item entity — tree structure (FR-010).
 *
 * Types:
 * - DIRECTORY: container, no route
 * - MENU: page with route
 * - BUTTON: UI button action (no navigation)
 * - API: backend API endpoint
 */
@Entity
@Table(name = "menu_items")
class MenuItemEntity : SnowflakePersistentAuditableEntity() {

    @Column(name = "parent_id")
    var parentId: Long? = null

    @Column(name = "domain_id", nullable = false)
    var domainId: Long = 0

    @Column(nullable = false, length = 100)
    lateinit var code: String

    @Column(nullable = false, length = 200)
    lateinit var name: String

    @Column(length = 100)
    var icon: String? = null

    @Column(length = 500)
    var path: String? = null

    @Column(name = "route_name", length = 200)
    var routeName: String? = null

    @Column(length = 500)
    var component: String? = null

    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int = 0

    @Column(nullable = false)
    var level: Int = 0

    @Column(name = "menu_type", nullable = false, length = 20)
    var menuType: String = TYPE_MENU

    @Column(name = "is_visible", nullable = false)
    var isVisible: Boolean = true

    @Column(name = "is_cacheable", nullable = false)
    var isCacheable: Boolean = false

    @Column(nullable = false, length = 20)
    var status: String = "ACTIVE"

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    var metadataJson: String? = null

    @Column(name = "translate_key", length = 200)
    var translateKey: String? = null

    companion object {
        const val TYPE_DIRECTORY = "DIRECTORY"
        const val TYPE_MENU = "MENU"
        const val TYPE_BUTTON = "BUTTON"
        const val TYPE_API = "API"
    }
}

/**
 * Permission definition for a menu item.
 * e.g., "view", "create", "edit", "delete", "export"
 */
@Entity
@Table(name = "menu_permissions",
    uniqueConstraints = [UniqueConstraint(columnNames = ["menu_id", "permission_code"])])
class MenuPermissionEntity : SnowflakePersistentAuditableEntity() {

    @Column(name = "menu_id", nullable = false)
    var menuId: Long = 0

    @Column(name = "permission_code", nullable = false, length = 50)
    lateinit var permissionCode: String

    @Column(nullable = false, length = 200)
    lateinit var name: String

    @Column(length = 500)
    var description: String? = null
}

/**
 * Role → Menu → Permission assignment.
 * Determines which permissions a role has on a specific menu item.
 */
@Entity
@Table(name = "role_menu_permissions",
    uniqueConstraints = [UniqueConstraint(columnNames = ["role_id", "menu_id", "permission_code"])])
class RoleMenuPermissionEntity : SnowflakePersistentAuditableEntity() {

    @Column(name = "role_id", nullable = false)
    var roleId: Long = 0

    @Column(name = "menu_id", nullable = false)
    var menuId: Long = 0

    @Column(name = "permission_code", nullable = false, length = 50)
    lateinit var permissionCode: String

    @Column(name = "is_granted", nullable = false)
    var isGranted: Boolean = true
}

/**
 * User-level override for menu permissions (BR-MENU-02).
 * Priority: User override > Role permission.
 */
@Entity
@Table(name = "user_menu_overrides",
    uniqueConstraints = [UniqueConstraint(columnNames = ["user_id", "menu_id", "permission_code"])])
class UserMenuOverrideEntity : SnowflakePersistentAuditableEntity() {

    @Column(name = "user_id", nullable = false)
    var userId: Long = 0

    @Column(name = "menu_id", nullable = false)
    var menuId: Long = 0

    @Column(name = "permission_code", nullable = false, length = 50)
    lateinit var permissionCode: String

    @Column(name = "is_granted", nullable = false)
    var isGranted: Boolean = true

    @Column(length = 500)
    var reason: String? = null
}
