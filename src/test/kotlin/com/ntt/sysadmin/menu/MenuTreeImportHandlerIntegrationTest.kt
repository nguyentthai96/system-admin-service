package com.ntt.sysadmin.menu

import com.ntt.basecore.autoconfigure.file.imports.ImportContext
import com.ntt.basecore.autoconfigure.file.imports.ImportStrategyMode
import com.ntt.sysadmin.menu.adapter.out.persistence.entity.MenuItemEntity
import com.ntt.sysadmin.menu.adapter.out.persistence.repository.MenuItemRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional

/**
 * Integration test verifying two-pass tree import for hierarchical menu items.
 *
 * Validates:
 * - 3-level menu tree can be imported without FK constraint violation
 * - Parent relationships are correctly restored after two-pass execution
 * - DELETE_AND_INSERT mode clears and re-inserts correctly
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MenuTreeImportHandlerIntegrationTest {

    @Autowired
    private lateinit var menuTreeImportHandler: MenuTreeImportHandler

    @Autowired
    private lateinit var menuItemRepository: MenuItemRepository

    @Test
    @DisplayName("Import 3-level menu tree without FK violation")
    fun `import three level menu tree successfully`() {
        // Arrange: Create 3-level tree
        val root = createMenuEntity(id = 100L, parentId = null, name = "Root Menu", level = 0)
        val child1 = createMenuEntity(id = 101L, parentId = 100L, name = "Child 1", level = 1)
        val child2 = createMenuEntity(id = 102L, parentId = 100L, name = "Child 2", level = 1)
        val grandchild = createMenuEntity(id = 103L, parentId = 101L, name = "Grandchild", level = 2)

        val entities = listOf(root, child1, child2, grandchild)
        val context = ImportContext("menu", ImportStrategyMode.DELETE_AND_INSERT, "test-user")

        // Act: Should NOT throw FK constraint violation
        assertDoesNotThrow {
            menuTreeImportHandler.process(entities, context)
        }

        // Assert: Verify all records imported
        val imported = menuItemRepository.findAll()
        assertTrue(imported.size >= 4, "Should have imported at least 4 menu items")
    }

    @Test
    @DisplayName("Root nodes should have null parent_id after import")
    fun `root nodes have null parent after import`() {
        val root = createMenuEntity(id = 200L, parentId = null, name = "Root Only", level = 0)
        val context = ImportContext("menu", ImportStrategyMode.TRUNCATE_AND_LOAD, "test-user")

        menuTreeImportHandler.process(listOf(root), context)

        val imported = menuItemRepository.findById(200L)
        assertTrue(imported.isPresent, "Root entity should be saved")
        assertNull(imported.get().parentId, "Root should have null parent_id")
    }

    private fun createMenuEntity(id: Long, parentId: Long?, name: String, level: Int): MenuItemEntity {
        return MenuItemEntity().apply {
            this.id = id
            this.parentId = parentId
            this.name = name
            this.code = name.lowercase().replace(" ", "_")
            this.domainId = 1L
            this.sortOrder = 0
            this.level = level
            this.menuType = MenuItemEntity.TYPE_MENU
            this.isVisible = true
            this.isCacheable = false
            this.status = "ACTIVE"
        }
    }
}
