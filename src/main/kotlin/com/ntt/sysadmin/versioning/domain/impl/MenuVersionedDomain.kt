package com.ntt.sysadmin.versioning.domain.impl

import com.fasterxml.jackson.databind.ObjectMapper
import com.ntt.basecore.domain.file.ColumnDefinition
import com.ntt.basecore.domain.file.ExportTemplate
import com.ntt.sysadmin.menu.adapter.out.persistence.entity.MenuItemEntity
import com.ntt.sysadmin.menu.adapter.out.persistence.repository.MenuItemRepository
import com.ntt.sysadmin.versioning.domain.VersionedConfigDomain
import com.ntt.sysadminservice.shared.exception.CircularReferenceException
import com.ntt.sysadminservice.shared.util.TreeBuilder
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Versioned configuration domain implementation for hierarchical menu trees (FR-001, FR-005).
 * Enforces topological hierarchy ordering, cycle detection, and sequence preservation.
 */
@Component
class MenuVersionedDomain(
    private val repository: MenuItemRepository,
    private val objectMapper: ObjectMapper
) : VersionedConfigDomain<MenuItemEntity> {

    private val log = LoggerFactory.getLogger(javaClass)

    override val domainName: String = "MENU_ITEM"

    override val entityClass: Class<MenuItemEntity> = MenuItemEntity::class.java

    override val naturalKeyExtractor: (MenuItemEntity) -> String = { it.code }

    override fun getEntityUpdatedAt(entity: MenuItemEntity): Long? = entity.updatedAt?.toEpochMilli()

    override fun fetchCurrentState(): List<MenuItemEntity> {
        return repository.findAll()
    }

    @Transactional
    override fun applyRollbackState(snapshotPayload: String, forceOverwrite: Boolean) {
        val listType = objectMapper.typeFactory.constructCollectionType(List::class.java, MenuItemEntity::class.java)
        val snapshotEntities: List<MenuItemEntity> = objectMapper.readValue(snapshotPayload, listType)

        log.info("Applying rollback for MENU_ITEM: restoring {} menu items in topological order", snapshotEntities.size)

        // Topological ordering: roots (level 0) first, then level 1, level 2, etc.
        val sortedNodes = snapshotEntities.sortedWith(
            compareBy<MenuItemEntity> { it.level }.thenBy { it.sortOrder }
        )

        // Track code -> newly persisted entity ID mapping to re-bind parent relationships safely
        val codeToIdMap = mutableMapOf<String, Long>()

        for (node in sortedNodes) {
            val existing = repository.findByCode(node.code)
            val entityToSave = existing ?: MenuItemEntity()

            entityToSave.apply {
                this.code = node.code
                this.name = node.name
                this.domainId = node.domainId
                this.icon = node.icon
                this.path = node.path
                this.routeName = node.routeName
                this.component = node.component
                this.sortOrder = node.sortOrder
                this.level = node.level
                this.menuType = node.menuType
                this.isVisible = node.isVisible
                this.isCacheable = node.isCacheable
                this.status = node.status

                // Re-bind parent if parent code is mapped, or maintain existing
                if (node.parentId != null) {
                    val parentNodeInSnapshot = snapshotEntities.find { it.id == node.parentId }
                    if (parentNodeInSnapshot != null && codeToIdMap.containsKey(parentNodeInSnapshot.code)) {
                        val resolvedParentId = codeToIdMap[parentNodeInSnapshot.code]
                        if (entityToSave.id != null && resolvedParentId != null) {
                            val hasCycle = TreeBuilder.detectCycle(entityToSave.id!!, resolvedParentId) { pid ->
                                repository.findById(pid).orElse(null)?.parentId
                            }
                            if (hasCycle) {
                                throw CircularReferenceException("Circular reference detected restoring menu '${node.code}'")
                            }
                        }
                        this.parentId = resolvedParentId
                    } else {
                        this.parentId = node.parentId
                    }
                } else {
                    this.parentId = null
                }
            }

            val saved = repository.save(entityToSave)
            if (saved.id != null) {
                codeToIdMap[saved.code] = saved.id!!
            }
        }
    }

    override fun exportTemplate(): ExportTemplate<MenuItemEntity> {
        return object : ExportTemplate<MenuItemEntity> {
            override val templateId: String = "menu-item-export"
            override val columns: List<ColumnDefinition> = listOf(
                ColumnDefinition("Code", "code"),
                ColumnDefinition("Name", "name"),
                ColumnDefinition("Type", "menuType"),
                ColumnDefinition("Path", "path"),
                ColumnDefinition("Route", "routeName"),
                ColumnDefinition("Component", "component"),
                ColumnDefinition("Sort Order", "sortOrder"),
                ColumnDefinition("Level", "level"),
                ColumnDefinition("Visible", "isVisible"),
                ColumnDefinition("Status", "status")
            )
            override fun dataQuery(filter: Map<String, Any>?): java.util.stream.Stream<MenuItemEntity> {
                return repository.findAll().stream()
            }
        }
    }
}
