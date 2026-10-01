package com.ntt.sysadminservice.organization

import com.ntt.basecore.autoconfigure.file.imports.ImportContext
import com.ntt.basecore.autoconfigure.file.imports.TwoPassTreeImportHandler
import com.ntt.sysadminservice.organization.adapter.out.persistence.entity.DepartmentEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

/**
 * Repository for DepartmentEntity.
 */
interface DepartmentRepository : JpaRepository<DepartmentEntity, Long>

/**
 * Import handler for hierarchical department tree using two-pass approach.
 * Handles self-referencing parent_id FK safely via deferred constraints.
 *
 * Dependencies: must import after domain_configs (FK on domain_id).
 */
@Component
class DepartmentTreeImportHandler(
    departmentRepository: DepartmentRepository,
    jdbcTemplate: JdbcTemplate
) : TwoPassTreeImportHandler<DepartmentEntity, Long>(
    tableName = "departments",
    repository = departmentRepository,
    jdbcTemplate = jdbcTemplate,
    order = 10
) {
    override val dependencies: Set<String> get() = setOf("domain_configs")

    override fun extractId(entity: DepartmentEntity): Long =
        checkNotNull(entity.id) { "DepartmentEntity id must not be null" }

    override fun extractParentId(entity: DepartmentEntity): Long? = entity.parentId

    override fun setParentId(entity: DepartmentEntity, parentId: Long?) {
        entity.parentId = parentId
    }

    override fun updateParentIdSql(): String =
        "UPDATE departments SET parent_id = ? WHERE id = ?"

    override fun cleanup(context: ImportContext) {
        // Department cache invalidation could be triggered here if needed
    }
}
