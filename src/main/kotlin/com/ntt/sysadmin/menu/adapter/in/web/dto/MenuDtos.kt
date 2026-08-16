package com.ntt.sysadmin.menu.adapter.`in`.web.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

/**
 * DTOs for Menu Permission Management API (FR-010).
 */

data class CreateMenuRequest(
    @field:NotBlank val code: String,
    @field:NotBlank val name: String,
    @field:NotNull val domainId: Long,
    val parentId: Long? = null,
    val icon: String? = null,
    val path: String? = null,
    val routeName: String? = null,
    val component: String? = null,
    val sortOrder: Int = 0,
    val menuType: String = "MENU",
    val isVisible: Boolean = true,
    val isCacheable: Boolean = false,
    val metadataJson: String? = null
)

data class UpdateMenuRequest(
    val name: String? = null,
    val icon: String? = null,
    val path: String? = null,
    val routeName: String? = null,
    val component: String? = null,
    val sortOrder: Int? = null,
    val menuType: String? = null,
    val isVisible: Boolean? = null,
    val isCacheable: Boolean? = null,
    val metadataJson: String? = null
)

data class MenuTreeNodeResponse(
    val id: Long,
    val code: String,
    val name: String,
    val icon: String?,
    val path: String?,
    val type: String,
    val isVisible: Boolean,
    val permissions: List<String>,
    val buttons: List<MenuButtonResponse>,
    val children: List<MenuTreeNodeResponse>
)

data class MenuButtonResponse(
    val code: String,
    val name: String,
    val permission: String
)

data class MenuTreeResponse(
    val menus: List<MenuTreeNodeResponse>
)

data class AddMenuPermissionRequest(
    @field:NotBlank val permissionCode: String,
    @field:NotBlank val name: String,
    val description: String? = null
)

data class AssignRoleMenuPermissionRequest(
    val menuPermissions: List<RoleMenuPermissionItem>
)

data class RoleMenuPermissionItem(
    val menuId: Long,
    val permissionCodes: List<String>
)

data class UserMenuOverrideRequest(
    val menuId: Long,
    @field:NotBlank val permissionCode: String,
    val isGranted: Boolean,
    val reason: String? = null
)
