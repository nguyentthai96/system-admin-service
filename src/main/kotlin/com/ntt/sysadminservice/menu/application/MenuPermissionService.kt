package com.ntt.sysadminservice.menu.application

import com.ntt.sysadminservice.shared.exception.SysAdminErrorCode
import com.ntt.sysadminservice.shared.exception.SysAdminException
import com.ntt.sysadminservice.shared.util.TreeBuilder
import jakarta.persistence.EntityManager
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration

/**
 * Menu permission service — button-level permission, role-menu assignment,
 * user override, Kafka-driven cache invalidation (FR-010).
 *
 * Permission resolution order: user override > role permission.
 * BUTTON type items are invisible in navigation — permission_code checked at API level.
 * Redis cache: user:{userId}:menu:{domainId} with TTL 5min.
 */
@Service
class MenuPermissionService(
    private val entityManager: EntityManager,
    private val redisTemplate: StringRedisTemplate
) {

    private val log = LoggerFactory.getLogger(MenuPermissionService::class.java)

    companion object {
        private const val MENU_CACHE_PREFIX = "user:"
        private const val MENU_CACHE_SUFFIX = ":menu:"
        private val CACHE_TTL = Duration.ofMinutes(5)
    }

    /**
     * Get filtered menu tree for a user based on role permissions + user overrides.
     * Cache in Redis with TTL 5min.
     */
    fun getUserMenuTree(userId: Long, domainId: Long): List<Map<String, Any?>> {
        val cacheKey = "${MENU_CACHE_PREFIX}${userId}${MENU_CACHE_SUFFIX}${domainId}"

        val cached = try {
            redisTemplate.opsForValue().get(cacheKey)
        } catch (e: Exception) {
            log.warn("Redis cache read failed: {}", e.message)
            null
        }
        if (cached != null) {
            return deserializeMenuTree(cached)
        }

        val permittedMenuIds = resolveUserPermissions(userId, domainId)

        @Suppress("UNCHECKED_CAST")
        val allMenus = entityManager
            .createNativeQuery("""
                SELECT id, parent_id, code, name, menu_type, permission_code, sort_order, tree_level, tree_path, active
                FROM menus
                WHERE domain_id = :domainId AND active = true
                ORDER BY sort_order
            """)
            .setParameter("domainId", domainId)
            .resultList as List<Array<Any?>>

        val filteredMenus = allMenus.filter { row ->
            val menuId = (row[0] as Number).toLong()
            val menuType = row[4] as? String ?: "MENU"
            menuType != "BUTTON" && menuId in permittedMenuIds
        }

        val tree = buildMenuTreeFromRows(filteredMenus)

        val serialized = serializeMenuTree(tree)
        try {
            redisTemplate.opsForValue().set(cacheKey, serialized, CACHE_TTL)
        } catch (e: Exception) {
            log.warn("Redis cache write failed: {}", e.message)
        }

        return tree
    }

    /**
     * Resolve effective menu permissions for a user.
     * Merge: role permissions UNION user GRANT overrides MINUS user DENY overrides.
     */
    private fun resolveUserPermissions(userId: Long, domainId: Long): Set<Long> {
        @Suppress("UNCHECKED_CAST")
        val roleMenuIds = entityManager
            .createNativeQuery("""
                SELECT DISTINCT rmp.menu_item_id 
                FROM role_menu_permissions rmp
                WHERE rmp.role_id IN (
                    SELECT role_id FROM user_roles WHERE user_id = :userId AND active = true
                ) AND rmp.active = true
            """)
            .setParameter("userId", userId)
            .resultList
            .map { (it as Number).toLong() }
            .toMutableSet()

        @Suppress("UNCHECKED_CAST")
        val overrides = entityManager
            .createNativeQuery("""
                SELECT menu_item_id, action FROM user_menu_overrides 
                WHERE user_id = :userId AND active = true
            """)
            .setParameter("userId", userId)
            .resultList as List<Array<Any?>>

        overrides.forEach { row ->
            val menuItemId = (row[0] as Number).toLong()
            val action = row[1] as String
            when (action) {
                "GRANT" -> roleMenuIds.add(menuItemId)
                "DENY" -> roleMenuIds.remove(menuItemId)
            }
        }

        // Include parent menu items for visible tree path
        val allIds = roleMenuIds.toMutableSet()
        roleMenuIds.forEach { menuId ->
            var parentId = getMenuParentId(menuId)
            while (parentId != null) {
                allIds.add(parentId)
                parentId = getMenuParentId(parentId)
            }
        }

        return allIds
    }

    /**
     * Assign menu permissions to a role.
     */
    @Transactional
    fun assignRolePermissions(roleId: Long, menuItemIds: List<Long>) {
        // Remove existing assignments for this role
        entityManager.createNativeQuery(
            "UPDATE role_menu_permissions SET active = false WHERE role_id = :roleId AND active = true"
        ).setParameter("roleId", roleId).executeUpdate()

        // Insert new assignments
        menuItemIds.forEach { menuItemId ->
            entityManager.createNativeQuery("""
                INSERT INTO role_menu_permissions (id, role_id, menu_item_id, created_at, active)
                VALUES (nextval('snowflake_seq'), :roleId, :menuItemId, NOW(), true)
                ON CONFLICT (role_id, menu_item_id) DO UPDATE SET active = true
            """)
                .setParameter("roleId", roleId)
                .setParameter("menuItemId", menuItemId)
                .executeUpdate()
        }

        log.info("Role permissions assigned: roleId={}, menuItems={}", roleId, menuItemIds.size)
    }

    /**
     * Set user-level override for a specific menu item (GRANT or DENY).
     */
    @Transactional
    fun setUserOverride(userId: Long, menuItemId: Long, action: String) {
        if (action !in setOf("GRANT", "DENY")) {
            throw SysAdminException(SysAdminErrorCode.GENERAL_ERROR, "Invalid override action: $action. Must be GRANT or DENY")
        }

        entityManager.createNativeQuery("""
            INSERT INTO user_menu_overrides (id, user_id, menu_item_id, action, created_at, active)
            VALUES (nextval('snowflake_seq'), :userId, :menuItemId, :action, NOW(), true)
            ON CONFLICT (user_id, menu_item_id) DO UPDATE SET action = :action, active = true
        """)
            .setParameter("userId", userId)
            .setParameter("menuItemId", menuItemId)
            .setParameter("action", action)
            .executeUpdate()

        invalidateUserMenuCache(userId)
        log.info("User override set: userId={}, menuItemId={}, action={}", userId, menuItemId, action)
    }

    /**
     * Remove user override for a specific menu item.
     */
    @Transactional
    fun removeUserOverride(userId: Long, menuItemId: Long) {
        entityManager.createNativeQuery(
            "UPDATE user_menu_overrides SET active = false WHERE user_id = :userId AND menu_item_id = :menuItemId"
        )
            .setParameter("userId", userId)
            .setParameter("menuItemId", menuItemId)
            .executeUpdate()

        invalidateUserMenuCache(userId)
    }

    /**
     * Check if user has a specific button-level permission.
     */
    fun hasButtonPermission(userId: Long, permissionCode: String, domainId: Long): Boolean {
        @Suppress("UNCHECKED_CAST")
        val buttonMenuId = entityManager
            .createNativeQuery("""
                SELECT id FROM menus 
                WHERE domain_id = :domainId AND permission_code = :code AND menu_type = 'BUTTON' AND active = true
            """)
            .setParameter("domainId", domainId)
            .setParameter("code", permissionCode)
            .resultList
            .firstOrNull() as? Number ?: return false

        val permitted = resolveUserPermissions(userId, domainId)
        return buttonMenuId.toLong() in permitted
    }

    /**
     * Invalidate user's menu cache (called by Kafka consumer on permission change).
     */
    fun invalidateUserMenuCache(userId: Long) {
        try {
            val pattern = "${MENU_CACHE_PREFIX}${userId}${MENU_CACHE_SUFFIX}*"
            val keys = redisTemplate.keys(pattern)
            if (!keys.isNullOrEmpty()) {
                redisTemplate.delete(keys)
                log.debug("Menu cache invalidated for userId={}, keys={}", userId, keys.size)
            }
        } catch (e: Exception) {
            log.warn("Failed to invalidate menu cache for userId={}: {}", userId, e.message)
        }
    }

    /**
     * Invalidate all menu caches (e.g., on role permission change).
     */
    fun invalidateAllMenuCaches() {
        try {
            val pattern = "${MENU_CACHE_PREFIX}*${MENU_CACHE_SUFFIX}*"
            val keys = redisTemplate.keys(pattern)
            if (!keys.isNullOrEmpty()) {
                redisTemplate.delete(keys)
                log.info("All menu caches invalidated: {} keys", keys.size)
            }
        } catch (e: Exception) {
            log.warn("Failed to invalidate all menu caches: {}", e.message)
        }
    }

    /**
     * Validate menu tree integrity before save.
     */
    @Transactional
    fun validateAndSaveMenu(menuId: Long?, parentId: Long?, domainId: Long) {
        if (parentId != null) {
            if (menuId != null && menuId == parentId) {
                throw SysAdminException(SysAdminErrorCode.CIRCULAR_REFERENCE, "Menu cannot be its own parent")
            }

            val hasCycle = TreeBuilder.detectCycle(
                nodeId = menuId ?: -1,
                parentId = parentId,
                getParent = { id -> getMenuParentId(id) }
            )
            if (hasCycle) {
                throw SysAdminException(SysAdminErrorCode.CIRCULAR_REFERENCE, "Circular reference detected in menu tree")
            }

            val parentDepth = getMenuDepth(parentId)
            val newDepth = parentDepth + 1
            if (newDepth > TreeBuilder.MAX_DEPTH) {
                throw SysAdminException(SysAdminErrorCode.MAX_DEPTH_EXCEEDED, "Menu tree depth exceeds maximum (${TreeBuilder.MAX_DEPTH})")
            }
        }

        log.debug("Menu validation passed: menuId={}, parentId={}", menuId, parentId)
    }

    /**
     * Batch validate all menus in a domain for consistency.
     */
    fun validateDomainMenuTree(domainId: Long): List<String> {
        val errors = mutableListOf<String>()

        @Suppress("UNCHECKED_CAST")
        val orphans = entityManager
            .createNativeQuery("""
                SELECT m.id, m.name FROM menus m 
                WHERE m.domain_id = :domainId AND m.parent_id IS NOT NULL 
                AND m.parent_id NOT IN (SELECT id FROM menus WHERE domain_id = :domainId AND active = true)
                AND m.active = true
            """)
            .setParameter("domainId", domainId)
            .resultList as List<Array<Any>>

        orphans.forEach { row ->
            errors.add("Orphan menu: id=${row[0]}, name=${row[1]}")
        }

        return errors
    }

    private fun getMenuParentId(menuId: Long): Long? {
        return try {
            entityManager
                .createNativeQuery("SELECT parent_id FROM menus WHERE id = :id AND active = true")
                .setParameter("id", menuId)
                .singleResult as? Long
        } catch (e: Exception) {
            null
        }
    }

    private fun getMenuDepth(menuId: Long): Int {
        var depth = 0
        var current: Long? = menuId
        val visited = mutableSetOf<Long>()
        while (current != null && visited.add(current)) {
            depth++
            current = getMenuParentId(current)
        }
        return depth
    }

    private fun buildMenuTreeFromRows(rows: List<Array<Any?>>): List<Map<String, Any?>> {
        val nodes = rows.map { row ->
            mutableMapOf<String, Any?>(
                "id" to (row[0] as Number).toLong(),
                "parentId" to (row[1] as? Number)?.toLong(),
                "code" to row[2],
                "name" to row[3],
                "menuType" to (row[4] ?: "MENU"),
                "permissionCode" to row[5],
                "sortOrder" to (row[6] as? Number)?.toInt(),
                "children" to mutableListOf<Map<String, Any?>>()
            )
        }

        val nodeMap = nodes.associateBy { it["id"] as Long }
        val roots = mutableListOf<Map<String, Any?>>()

        nodes.forEach { node ->
            val parentId = node["parentId"] as? Long
            if (parentId == null || !nodeMap.containsKey(parentId)) {
                roots.add(node)
            } else {
                @Suppress("UNCHECKED_CAST")
                (nodeMap[parentId]?.get("children") as? MutableList<Map<String, Any?>>)?.add(node)
            }
        }

        return roots
    }

    private fun serializeMenuTree(tree: List<Map<String, Any?>>): String {
        return com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(tree)
    }

    private fun deserializeMenuTree(json: String): List<Map<String, Any?>> {
        @Suppress("UNCHECKED_CAST")
        return com.fasterxml.jackson.databind.ObjectMapper()
            .readValue(json, List::class.java) as List<Map<String, Any?>>
    }
}
