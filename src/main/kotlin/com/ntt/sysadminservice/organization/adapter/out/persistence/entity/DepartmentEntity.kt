package com.ntt.sysadminservice.organization.adapter.out.persistence.entity

import com.ntt.sysadminservice.shared.persistence.TreeEntity
import jakarta.persistence.*

/**
 * Department entity — recursive tree hierarchy (FR-011).
 * Max 10 levels, DFS cycle detection.
 */
@Entity
@Table(
    name = "departments",
    uniqueConstraints = [UniqueConstraint(columnNames = ["domain_id", "code"])]
)
class DepartmentEntity : TreeEntity() {

    @Column(name = "domain_id", nullable = false)
    var domainId: Long = 0

    @Column(length = 500)
    var description: String? = null

    @Column(name = "manager_user_id")
    var managerUserId: Long? = null

    @Column(nullable = false, length = 20)
    var status: String = "ACTIVE"

    /** Transient children for tree building */
    @Transient
    var children: MutableList<DepartmentEntity> = mutableListOf()
}
