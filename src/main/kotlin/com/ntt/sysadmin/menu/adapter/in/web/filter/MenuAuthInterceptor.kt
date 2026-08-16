package com.ntt.sysadmin.menu.adapter.`in`.web.filter

import com.ntt.sysadmin.menu.adapter.out.cache.MenuPermissionCacheAdapter
import com.ntt.sysadmin.menu.adapter.out.persistence.repository.MenuItemRepository
import com.ntt.sysadmin.menu.adapter.out.persistence.repository.RoleMenuPermissionRepository
import com.ntt.sysadmin.menu.adapter.out.persistence.repository.UserMenuOverrideRepository
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor

/**
 * Menu Auth Interceptor (FR-010).
 *
 * Intercepts requests and checks user permissions against the menu permission model.
 * If a user doesn't have permission, the request is rejected with HTTP 403.
 *
 * Priority order (BR-MENU-02):
 * 1. User override (highest)
 * 2. Department permission
 * 3. Role permission (lowest)
 *
 * Uses Redis cache for performance (< 50ms latency target).
 */
@Component
class MenuAuthInterceptor(
    private val menuItemRepository: MenuItemRepository,
    private val roleMenuPermissionRepository: RoleMenuPermissionRepository,
    private val userMenuOverrideRepository: UserMenuOverrideRepository,
    private val cacheAdapter: MenuPermissionCacheAdapter
) : HandlerInterceptor {

    private val log = LoggerFactory.getLogger(MenuAuthInterceptor::class.java)

    companion object {
        /** Paths that bypass menu permission check. */
        private val EXCLUDED_PATHS = setOf(
            "/actuator", "/health", "/api/public", "/error"
        )

        /** Cache key prefix for permission check results. */
        private const val PERM_CHECK_CACHE_PREFIX = "menu:auth:check:"
    }

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        val path = request.requestURI
        val method = request.method

        // Skip excluded paths
        if (EXCLUDED_PATHS.any { path.startsWith(it) }) {
            return true
        }

        // Extract user context from JWT claims (set by security filter)
        val userId = request.getAttribute("userId") as? Long
        val roleIds = request.getAttribute("roleIds") as? List<Long>

        if (userId == null || roleIds == null) {
            // No auth context — let Spring Security handle
            return true
        }

        // Map request path to menu code
        val menuCode = resolveMenuCode(path)
        if (menuCode == null) {
            // No menu mapping — allow (endpoint not protected by menu system)
            return true
        }

        // Determine required permission from HTTP method
        val requiredPermission = mapMethodToPermission(method)

        // Check permission (cached)
        val hasPermission = checkPermission(userId, roleIds, menuCode, requiredPermission)

        if (!hasPermission) {
            log.warn("MENU_AUTH_REJECTED userId={} path={} method={} menuCode={} permission={}",
                userId, path, method, menuCode, requiredPermission)
            response.status = HttpServletResponse.SC_FORBIDDEN
            response.contentType = "application/json"
            response.writer.write(
                """{"error":"FORBIDDEN","message":"You do not have permission to access this resource","menuCode":"$menuCode","requiredPermission":"$requiredPermission"}"""
            )
            return false
        }

        return true
    }

    /**
     * Check if user has the required permission on a menu item.
     *
     * Algorithm (priority: user override > role):
     * 1. Check user override — if explicitly denied, reject
     * 2. Check role permissions — if any role grants, allow
     */
    private fun checkPermission(
        userId: Long,
        roleIds: List<Long>,
        menuCode: String,
        permission: String
    ): Boolean {
        // Try Redis cache first
        val cacheKey = "$PERM_CHECK_CACHE_PREFIX$userId:$menuCode:$permission"
        val cached = cacheAdapter.getCachedPermissionCheck(cacheKey)
        if (cached != null) {
            return cached == "true"
        }

        // Resolve menu item
        val menuItem = menuItemRepository.findByCode(menuCode) ?: return true // Unknown menu = allow
        val menuId = menuItem.id ?: return true

        // Check user override (BR-MENU-02: highest priority)
        val userOverrides = userMenuOverrideRepository.findByUserIdAndMenuId(userId, menuId)
        val override = userOverrides.find { it.permissionCode == permission }
        if (override != null) {
            val result = override.isGranted
            cacheAdapter.cachePermissionCheck(cacheKey, result.toString())
            return result
        }

        // Check role permissions
        val rolePerms = roleMenuPermissionRepository.findByRoleIdInAndMenuIdAndIsGrantedTrue(roleIds, menuId)
        val hasRolePerm = rolePerms.any { it.permissionCode == permission }

        cacheAdapter.cachePermissionCheck(cacheKey, hasRolePerm.toString())
        return hasRolePerm
    }

    /**
     * Map request path to menu code.
     * Convention: /api/admin/{module}/... → menu code = {module}
     */
    private fun resolveMenuCode(path: String): String? {
        val segments = path.removePrefix("/api/").split("/").filter { it.isNotBlank() }
        return if (segments.size >= 2) {
            // e.g., /api/admin/users → "admin-users"
            "${segments[0]}-${segments[1]}"
        } else if (segments.isNotEmpty()) {
            segments[0]
        } else {
            null
        }
    }

    /**
     * Map HTTP method to permission code.
     */
    private fun mapMethodToPermission(method: String): String = when (method.uppercase()) {
        "GET" -> "view"
        "POST" -> "create"
        "PUT", "PATCH" -> "edit"
        "DELETE" -> "delete"
        else -> "view"
    }
}
