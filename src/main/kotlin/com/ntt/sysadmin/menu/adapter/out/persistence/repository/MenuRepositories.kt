package com.ntt.sysadmin.menu.adapter.out.persistence.repository

import com.ntt.sysadmin.menu.adapter.out.persistence.entity.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface MenuItemRepository : JpaRepository<MenuItemEntity, Long> {

    fun findByDomainIdAndIsActiveTrue(domainId: Long): List<MenuItemEntity>

    fun findByParentIdAndIsActiveTrue(parentId: Long?): List<MenuItemEntity>

    @Query("SELECT m FROM MenuItemEntity m WHERE m.domainId = :domainId AND m.isActive = true ORDER BY m.level ASC, m.sortOrder ASC")
    fun findAllByDomainOrdered(domainId: Long): List<MenuItemEntity>

    fun findByCodeAndDomainId(code: String, domainId: Long): MenuItemEntity?

    fun findByCode(code: String): MenuItemEntity?
}

@Repository
interface MenuPermissionRepository : JpaRepository<MenuPermissionEntity, Long> {

    fun findByMenuId(menuId: Long): List<MenuPermissionEntity>

    fun findByMenuIdIn(menuIds: List<Long>): List<MenuPermissionEntity>
}

@Repository
interface RoleMenuPermissionRepository : JpaRepository<RoleMenuPermissionEntity, Long> {

    fun findByRoleIdAndIsGrantedTrue(roleId: Long): List<RoleMenuPermissionEntity>

    fun findByRoleIdInAndIsGrantedTrue(roleIds: List<Long>): List<RoleMenuPermissionEntity>

    @Query("SELECT rmp FROM RoleMenuPermissionEntity rmp WHERE rmp.roleId IN :roleIds AND rmp.menuId = :menuId AND rmp.isGranted = true")
    fun findByRoleIdsAndMenuId(roleIds: List<Long>, menuId: Long): List<RoleMenuPermissionEntity>

    fun deleteByRoleIdAndMenuId(roleId: Long, menuId: Long)

    fun findByRoleIdInAndMenuIdAndIsGrantedTrue(roleIds: List<Long>, menuId: Long): List<RoleMenuPermissionEntity>
}

@Repository
interface UserMenuOverrideRepository : JpaRepository<UserMenuOverrideEntity, Long> {

    fun findByUserId(userId: Long): List<UserMenuOverrideEntity>

    fun findByUserIdAndMenuId(userId: Long, menuId: Long): List<UserMenuOverrideEntity>
}
