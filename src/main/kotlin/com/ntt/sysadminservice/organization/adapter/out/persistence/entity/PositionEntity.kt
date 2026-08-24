package com.ntt.sysadminservice.organization.adapter.out.persistence.entity

import com.ntt.basecore.model.id.SnowflakePersistentAuditableEntity
import jakarta.persistence.*

/**
 * Position entity within a department (FR-011).
 */
@Entity
@Table(
    name = "positions",
    uniqueConstraints = [UniqueConstraint(columnNames = ["department_id", "code"])]
)
class PositionEntity : SnowflakePersistentAuditableEntity() {

    @Column(name = "department_id", nullable = false)
    var departmentId: Long = 0

    @Column(nullable = false, length = 50)
    lateinit var code: String

    @Column(nullable = false, length = 200)
    lateinit var name: String

    @Column(length = 500)
    var description: String? = null

    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int = 0

    @Column(nullable = false, length = 20)
    var status: String = "ACTIVE"
}
