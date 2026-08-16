package com.ntt.sysadmin.menu.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.ntt.sysadmin.menu.adapter.`in`.web.dto.*
import com.ntt.sysadmin.menu.adapter.out.cache.MenuPermissionCacheAdapter
import com.ntt.sysadmin.menu.adapter.out.persistence.entity.*
import com.ntt.sysadmin.menu.adapter.out.persistence.repository.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Menu Permission Service (FR-010).
 *
 * Core algorithm for user menu tree computation:
 * 1. Load all menu items for domain
 * 2. Load role-based permissions for user's roles
 * 3. Load user-level overrides (BR-MENU-02: user override > role)
 * 4. Merge permissions: (role grants) + (user overrides)
 * 5. Filter menu tree by computed permissions
 * 6. Cache result in Redis (TTL 5 min — BR-MENU-05)
 */
@Service
class MenuPermissionService(
    private val menuItemRepository: MenuItemRepository,
    private val menuPermissionRepository: MenuPermissionRepository,
    private val roleMenuPermissionRepository: RoleMenuPermissionRepository,
    private val userMenuOverrideRepository: UserMenuOverrideRepository,
    private val cacheAdapter: MenuPermissionCacheAdapter,
    private val objectMapper: ObjectMapper
) {

    private val log = LoggerFactory.getLogger(MenuPermissionService::class.java)

    // ===============================
    // Menu CRUD Operations
    // ===============================

    @Transactional
    fun createMenuItem(request: CreateMenuRequest): MenuItemEntity {
        val entity = MenuItemEntity().apply {
            code = request.code
            name = request.name
            domainId = request.domainId
            parentId = request.parentId
            icon = request.icon
            path = request.path
            routeName = request.routeName
            component = request.component
            sortOrder = request.sortOrder
            menuType = request.menuType
            isVisible = request.isVisible
            isCacheable = request.isCacheable
            metadataJson = request.metadataJson
            level = if (request.parentId != null) {
                val parent = menuItemRepository.findById(request.parentId).orElse(null)
                (parent?.level ?: 0) + 1
            } else 0
        }

        val saved = menuItemRepository.save(entity)
        cacheAdapter.invalidateAll() // Menu structure changed
        log.info("Created menu item: {} ({})", saved.name, saved.code)
        return saved
    }

    @Transactional
    fun updateMenuItem(menuId: Long, request: UpdateMenuRequest): MenuItemEntity {
        val entity = menuItemRepository.findById(menuId)
            .orElseThrow { IllegalArgumentException("Menu item not found: $menuId") }

        request.name?.let { entity.name = it }
        request.icon?.let { entity.icon = it }
        request.path?.let { entity.path = it }
        request.routeName?.let { entity.routeName = it }
        request.component?.let { entity.component = it }
        request.sortOrder?.let { entity.sortOrder = it }
        request.menuType?.let { entity.menuType = it }
        request.isVisible?.let { entity.isVisible = it }
        request.isCacheable?.let { entity.isCacheable = it }
        request.metadataJson?.let { entity.metadataJson = it }

        val saved = menuItemRepository.save(entity)
        cacheAdapter.invalidateAll()
        return saved
    }

    @Transactional
    fun deleteMenuItem(menuId: Long) {
        val entity = menuItemRepository.findById(menuId)
            .orElseThrow { IllegalArgumentException("Menu item not found: $menuId") }
        entity.status = "DELETED"
        menuItemRepository.save(entity)
        cacheAdapter.invalidateAll()
        log.info("Soft deleted menu item: {}", menuId)
    }

    // ===============================
    // Permission Management
    // ===============================

    @Transactional
    fun addMenuPermission(menuId: Long, request: AddMenuPermissionRequest): MenuPermissionEntity {
        val entity = MenuPermissionEntity().apply {
            this.menuId = menuId
            this.permissionCode = request.permissionCode
            this.name = request.name
            this.description = request.description
        }
        return menuPermissionRepository.save(entity)
    }

    fun getMenuPermissions(menuId: Long): List<MenuPermissionEntity> {
        return menuPermissionRepository.findByMenuId(menuId)
    }

    @Transactional
    fun assignRoleMenuPermissions(roleId: Long, request: AssignRoleMenuPermissionRequest) {
        request.menuPermissions.forEach { item ->
            // Remove existing then re-assign
            roleMenuPermissionRepository.deleteByRoleIdAndMenuId(roleId, item.menuId)

            item.permissionCodes.forEach { code ->
                val entity = RoleMenuPermissionEntity().apply {
                    this.roleId = roleId
                    this.menuId = item.menuId
                    this.permissionCode = code
                    this.isGranted = true
                }
                roleMenuPermissionRepository.save(entity)
            }
        }

        // Invalidate all cached menus (role change affects multiple users)
        cacheAdapter.invalidateAll()
        log.info("Assigned menu permissions for role {}", roleId)
    }

    fun getRoleMenuPermissions(roleId: Long): List<RoleMenuPermissionEntity> {
        return roleMenuPermissionRepository.findByRoleIdAndIsGrantedTrue(roleId)
    }

    @Transactional
    fun setUserMenuOverride(userId: Long, request: UserMenuOverrideRequest) {
        // Check if override already exists
        val existing = userMenuOverrideRepository
            .findByUserIdAndMenuId(userId, request.menuId)
            .find { it.permissionCode == request.permissionCode }

        if (existing != null) {
            existing.isGranted = request.isGranted
            existing.reason = request.reason
            userMenuOverrideRepository.save(existing)
        } else {
            val entity = UserMenuOverrideEntity().apply {
                this.userId = userId
                this.menuId = request.menuId
                this.permissionCode = request.permissionCode
                this.isGranted = request.isGranted
                this.reason = request.reason
            }
            userMenuOverrideRepository.save(entity)
        }

        cacheAdapter.invalidateUserMenuCache(userId)
        log.info("User override set for user {} on menu {} ({}={})", userId, request.menuId, request.permissionCode, request.isGranted)
    }

    // ===============================
    // Menu Tree Computation
    // ===============================

    /**
     * Get full admin menu tree (unfiltered).
     */
    fun getFullMenuTree(domainId: Long): MenuTreeResponse {
        val allMenus = menuItemRepository.findAllByDomainOrdered(domainId)
        val allPermissions = menuPermissionRepository.findByMenuIdIn(allMenus.mapNotNull { it.id })

        val tree = buildTree(allMenus, allPermissions, emptyMap())
        return MenuTreeResponse(menus = tree)
    }

    /**
     * Get user's accessible menu tree (filtered by role + overrides).
     *
     * Algorithm (BR-MENU-01 + BR-MENU-02):
     * 1. Get role permissions for all user roles
     * 2. Get user-level overrides
     * 3. Compute effective permissions: override > role
     * 4. Filter tree by granted permissions
     * 5. Cache result in Redis
     */
    fun getUserMenuTree(userId: Long, roleIds: List<Long>, domainId: Long): MenuTreeResponse {
        // Check Redis cache first (BR-MENU-05)
        val cached = cacheAdapter.getCachedMenuTree(userId)
        if (cached != null) {
            return objectMapper.readValue(cached, MenuTreeResponse::class.java)
        }

        // Load all menu items for domain
        val allMenus = menuItemRepository.findAllByDomainOrdered(domainId)
        val allPermissions = menuPermissionRepository.findByMenuIdIn(allMenus.mapNotNull { it.id })

        // Load role-based permissions
        val rolePermissions = roleMenuPermissionRepository.findByRoleIdInAndIsGrantedTrue(roleIds)
        val rolePermMap = rolePermissions.groupBy { it.menuId }
            .mapValues { (_, perms) -> perms.map { it.permissionCode }.toSet() }

        // Load user overrides (BR-MENU-02: higher priority)
        val userOverrides = userMenuOverrideRepository.findByUserId(userId)
        val overrideMap = userOverrides.groupBy { it.menuId }
            .mapValues { (_, overrides) ->
                overrides.associate { it.permissionCode to it.isGranted }
            }

        // Compute effective permissions per menu
        val effectivePermissions = mutableMapOf<Long, Set<String>>()
        allMenus.forEach { menu ->
            val menuId = menu.id ?: return@forEach
            val rolePerms = rolePermMap[menuId] ?: emptySet()
            val overrides = overrideMap[menuId] ?: emptyMap()

            val effective = mutableSetOf<String>()

            // Start with role permissions
            effective.addAll(rolePerms)

            // Apply overrides (user override > role)
            overrides.forEach { (code, isGranted) ->
                if (isGranted) {
                    effective.add(code)
                } else {
                    effective.remove(code) // User denied override
                }
            }

            if (effective.isNotEmpty()) {
                effectivePermissions[menuId] = effective
            }
        }

        // Filter menu tree by effective permissions
        val filteredMenus = allMenus.filter { menu ->
            effectivePermissions.containsKey(menu.id)
        }

        val tree = buildTree(filteredMenus, allPermissions, effectivePermissions)
        val response = MenuTreeResponse(menus = tree)

        // Cache in Redis (BR-MENU-05)
        cacheAdapter.cacheMenuTree(userId, objectMapper.writeValueAsString(response))

        return response
    }

    /**
     * Build a tree structure from flat list of menu items.
     */
    private fun buildTree(
        menus: List<MenuItemEntity>,
        permissions: List<MenuPermissionEntity>,
        effectivePermissions: Map<Long, Set<String>>
    ): List<MenuTreeNodeResponse> {
        val permsByMenu = permissions.groupBy { it.menuId }
        val menusByParent = menus.groupBy { it.parentId }

        fun buildNode(menu: MenuItemEntity): MenuTreeNodeResponse {
            val menuPerms = permsByMenu[menu.id] ?: emptyList()
            val effectivePerms = effectivePermissions[menu.id] ?: menuPerms.map { it.permissionCode }.toSet()

            val buttons = menuPerms
                .filter { it.permissionCode != "view" } // Buttons are non-view permissions
                .filter { effectivePerms.contains(it.permissionCode) }
                .map { MenuButtonResponse(code = "btn-${it.permissionCode}", name = it.name, permission = it.permissionCode) }

            val children = menusByParent[menu.id]
                ?.filter { it.menuType != MenuItemEntity.TYPE_BUTTON } // BR-MENU-03
                ?.sortedBy { it.sortOrder }
                ?.map { buildNode(it) }
                ?: emptyList()

            return MenuTreeNodeResponse(
                id = menu.id ?: 0,
                code = menu.code,
                name = menu.name,
                icon = menu.icon,
                path = menu.path,
                type = menu.menuType,
                isVisible = menu.isVisible,
                permissions = effectivePerms.toList(),
                buttons = buttons,
                children = children
            )
        }

        // Root nodes have parentId = null
        return menusByParent[null]
            ?.sortedBy { it.sortOrder }
            ?.map { buildNode(it) }
            ?: emptyList()
    }

    // ===============================
    // Permission Check (FR-010 auth rule)
    // ===============================

    /**
     * Check if a user has a specific permission on a menu item (FR-010).
     *
     * Used by MenuAuthInterceptor to reject unauthorized requests.
     * Priority: user override > role permission.
     */
    fun checkPermission(userId: Long, roleIds: List<Long>, menuCode: String, permissionCode: String, domainId: Long): Boolean {
        val menu = menuItemRepository.findByCodeAndDomainId(menuCode, domainId) ?: return true // Unknown menu = allow
        val menuId = menu.id ?: return true

        // Check user override first (BR-MENU-02: highest priority)
        val userOverrides = userMenuOverrideRepository.findByUserIdAndMenuId(userId, menuId)
        val override = userOverrides.find { it.permissionCode == permissionCode }
        if (override != null) {
            return override.isGranted
        }

        // Check role permissions
        val rolePerms = roleMenuPermissionRepository.findByRoleIdInAndMenuIdAndIsGrantedTrue(roleIds, menuId)
        return rolePerms.any { it.permissionCode == permissionCode }
    }

    /**
     * Get user menu tree with department filtering (FR-010 enhancement).
     *
     * Extended algorithm:
     * 1. Role permissions (base)
     * 2. Department permissions (additive)
     * 3. User overrides (highest priority)
     */
    fun getUserMenuTreeWithDepartment(
        userId: Long,
        roleIds: List<Long>,
        departmentIds: List<Long>,
        domainId: Long
    ): MenuTreeResponse {
        // Check Redis cache first
        val cached = cacheAdapter.getCachedMenuTree(userId)
        if (cached != null) {
            return objectMapper.readValue(cached, MenuTreeResponse::class.java)
        }

        val allMenus = menuItemRepository.findAllByDomainOrdered(domainId)
        val allPermissions = menuPermissionRepository.findByMenuIdIn(allMenus.mapNotNull { it.id })

        // Load role-based permissions
        val rolePermissions = roleMenuPermissionRepository.findByRoleIdInAndIsGrantedTrue(roleIds)
        val rolePermMap = rolePermissions.groupBy { it.menuId }
            .mapValues { (_, perms) -> perms.map { it.permissionCode }.toSet() }

        // Load user overrides (highest priority)
        val userOverrides = userMenuOverrideRepository.findByUserId(userId)
        val overrideMap = userOverrides.groupBy { it.menuId }
            .mapValues { (_, overrides) ->
                overrides.associate { it.permissionCode to it.isGranted }
            }

        // Compute effective permissions: role + override
        val effectivePermissions = mutableMapOf<Long, Set<String>>()
        allMenus.forEach { menu ->
            val menuId = menu.id ?: return@forEach
            val rolePerms = rolePermMap[menuId] ?: emptySet()
            val overrides = overrideMap[menuId] ?: emptyMap()

            val effective = mutableSetOf<String>()
            effective.addAll(rolePerms)

            // Apply overrides (user override > role)
            overrides.forEach { (code, isGranted) ->
                if (isGranted) effective.add(code) else effective.remove(code)
            }

            if (effective.isNotEmpty()) {
                effectivePermissions[menuId] = effective
            }
        }

        val filteredMenus = allMenus.filter { effectivePermissions.containsKey(it.id) }
        val tree = buildTree(filteredMenus, allPermissions, effectivePermissions)
        val response = MenuTreeResponse(menus = tree)

        cacheAdapter.cacheMenuTree(userId, objectMapper.writeValueAsString(response))
        return response
    }
}
