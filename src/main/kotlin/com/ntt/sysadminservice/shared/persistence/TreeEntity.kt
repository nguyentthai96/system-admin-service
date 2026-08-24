package com.ntt.sysadminservice.shared.persistence

import com.ntt.basecore.model.id.SnowflakePersistentAuditableEntity
import jakarta.persistence.*

/**
 * Abstract tree entity — supports recursive hierarchical structures.
 * Used by DepartmentEntity, MenuEntity, etc.
 * Max depth: 10 levels, DFS cycle detection.
 */
@MappedSuperclass
open class TreeEntity : SnowflakePersistentAuditableEntity() {

    @Column(name = "parent_id")
    var parentId: Long? = null

    @Column(nullable = false, length = 200)
    open lateinit var name: String

    @Column(nullable = false, length = 50)
    open lateinit var code: String

    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int = 0

    @Column(name = "tree_level", nullable = false)
    var treeLevel: Int = 0

    @Column(name = "tree_path", length = 1000)
    var treePath: String? = null
}
