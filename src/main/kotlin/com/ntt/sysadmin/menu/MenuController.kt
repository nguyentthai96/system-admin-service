package com.ntt.sysadmin.menu

import com.ntt.sysadmin.menu.adapter.`in`.web.dto.*
import com.ntt.sysadmin.menu.application.MenuPermissionService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * REST Controller for Menu Permission Management (FR-010).
 *
 * Handles:
 * - Menu CRUD (tree structure)
 * - Permission definitions per menu
 * - Role → Menu permission assignments
 * - User-level overrides (BR-MENU-02)
 * - User's accessible menu tree (filtered + cached)
 */
@RestController
@RequestMapping("/api/admin")
class MenuController(
    private val menuPermissionService: MenuPermissionService
) {

    // ===============================
    // Menu CRUD
    // ===============================

    /**
     * Get full menu tree for admin view.
     */
    @GetMapping("/menus/tree")
    fun getMenuTree(
        @RequestParam domainId: Long
    ): ResponseEntity<MenuTreeResponse> {
        return ResponseEntity.ok(menuPermissionService.getFullMenuTree(domainId))
    }

    /**
     * Get user's accessible menu tree (filtered by roles + overrides).
     * Uses Redis cache with 5-min TTL (BR-MENU-05).
     */
    @GetMapping("/menus/user-tree")
    fun getUserMenuTree(
        @RequestHeader("X-User-Id") userId: Long,
        @RequestParam roleIds: List<Long>,
        @RequestParam domainId: Long
    ): ResponseEntity<MenuTreeResponse> {
        return ResponseEntity.ok(menuPermissionService.getUserMenuTree(userId, roleIds, domainId))
    }

    /**
     * Create a new menu item.
     */
    @PostMapping("/menus")
    fun createMenu(
        @Valid @RequestBody request: CreateMenuRequest
    ): ResponseEntity<Map<String, Any>> {
        val item = menuPermissionService.createMenuItem(request)
        return ResponseEntity.ok(mapOf<String, Any>("id" to (item.id ?: 0), "code" to item.code, "name" to item.name))
    }

    /**
     * Update an existing menu item.
     */
    @PutMapping("/menus/{id}")
    fun updateMenu(
        @PathVariable id: Long,
        @RequestBody request: UpdateMenuRequest
    ): ResponseEntity<Map<String, Any>> {
        val item = menuPermissionService.updateMenuItem(id, request)
        return ResponseEntity.ok(mapOf<String, Any>("id" to (item.id ?: 0), "name" to item.name, "updated" to true))
    }

    /**
     * Soft delete a menu item.
     */
    @DeleteMapping("/menus/{id}")
    fun deleteMenu(@PathVariable id: Long): ResponseEntity<Map<String, Any>> {
        menuPermissionService.deleteMenuItem(id)
        return ResponseEntity.ok(mapOf("id" to id, "deleted" to true))
    }

    // ===============================
    // Menu Permissions
    // ===============================

    /**
     * List permissions for a menu item.
     */
    @GetMapping("/menus/{id}/permissions")
    fun getMenuPermissions(@PathVariable id: Long): ResponseEntity<List<Map<String, Any?>>> {
        val perms = menuPermissionService.getMenuPermissions(id)
        return ResponseEntity.ok(perms.map {
            mapOf("id" to it.id, "code" to it.permissionCode, "name" to it.name, "description" to it.description)
        })
    }

    /**
     * Add a permission to a menu item.
     */
    @PostMapping("/menus/{id}/permissions")
    fun addMenuPermission(
        @PathVariable id: Long,
        @Valid @RequestBody request: AddMenuPermissionRequest
    ): ResponseEntity<Map<String, Any>> {
        val perm = menuPermissionService.addMenuPermission(id, request)
        return ResponseEntity.ok(mapOf<String, Any>("id" to (perm.id ?: 0), "code" to perm.permissionCode))
    }

    // ===============================
    // Role → Menu Assignments
    // ===============================

    /**
     * Assign menu permissions to a role.
     */
    @PostMapping("/roles/{roleId}/menus")
    fun assignRoleMenuPermissions(
        @PathVariable roleId: Long,
        @Valid @RequestBody request: AssignRoleMenuPermissionRequest
    ): ResponseEntity<Map<String, Any>> {
        menuPermissionService.assignRoleMenuPermissions(roleId, request)
        return ResponseEntity.ok(mapOf("roleId" to roleId, "assigned" to true))
    }

    /**
     * Get a role's menu permissions.
     */
    @GetMapping("/roles/{roleId}/menus")
    fun getRoleMenuPermissions(@PathVariable roleId: Long): ResponseEntity<List<Map<String, Any>>> {
        val perms = menuPermissionService.getRoleMenuPermissions(roleId)
        return ResponseEntity.ok(perms.map {
            mapOf("roleId" to it.roleId, "menuId" to it.menuId, "permission" to it.permissionCode)
        })
    }

    // ===============================
    // User Overrides (BR-MENU-02)
    // ===============================

    /**
     * Set user-level menu permission override.
     * User override priority > Role permission.
     */
    @PostMapping("/users/{userId}/menu-overrides")
    fun setUserMenuOverride(
        @PathVariable userId: Long,
        @Valid @RequestBody request: UserMenuOverrideRequest
    ): ResponseEntity<Map<String, Any>> {
        menuPermissionService.setUserMenuOverride(userId, request)
        return ResponseEntity.ok(mapOf("userId" to userId, "overrideSet" to true))
    }
}
