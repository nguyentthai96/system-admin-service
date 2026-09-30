package com.ntt.sysadmin.menu

import com.ntt.basecore.autoconfigure.file.imports.ImportContext
import com.ntt.basecore.autoconfigure.file.imports.ImportStrategyMode
import com.ntt.basecore.autoconfigure.file.imports.TwoPassTreeImportHandler
import com.ntt.sysadmin.menu.adapter.out.persistence.entity.MenuItemEntity
import com.ntt.sysadmin.menu.adapter.out.persistence.repository.MenuItemRepository
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

/**
 * Import handler for hierarchical menu items using two-pass approach.
 * Handles self-referencing parent_id FK safely via deferred constraints.
 *
 * Dependencies: must import after domain_configs (FK on domain_id).
 */
@Component
class MenuTreeImportHandler(
    menuItemRepository: MenuItemRepository,
    jdbcTemplate: JdbcTemplate
) : TwoPassTreeImportHandler<MenuItemEntity, Long>(
    tableName = "menu_items",
    repository = menuItemRepository,
    jdbcTemplate = jdbcTemplate,
    order = 10
) {
    override val dependencies: Set<String> get() = setOf("domain_configs")

    override fun extractId(entity: MenuItemEntity): Long = entity.id

    override fun extractParentId(entity: MenuItemEntity): Long? = entity.parentId

    override fun setParentId(entity: MenuItemEntity, parentId: Long?) {
        entity.parentId = parentId
    }

    override fun updateParentIdSql(): String =
        "UPDATE menu_items SET parent_id = ? WHERE id = ?"

    override fun cleanup(context: ImportContext) {
        // Menu cache invalidation could be triggered here if needed
    }
}
