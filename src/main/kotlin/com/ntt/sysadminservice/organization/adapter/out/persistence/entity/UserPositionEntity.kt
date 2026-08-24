package com.ntt.sysadminservice.organization.adapter.out.persistence.entity

import com.ntt.basecore.model.id.SnowflakePersistentAuditableEntity
import jakarta.persistence.*
import java.time.Instant

/**
 * User ↔ Position assignment (FR-011).
 */
@Entity
@Table(
    name = "user_positions",
    uniqueConstraints = [UniqueConstraint(columnNames = ["user_id", "position_id"])]
)
class UserPositionEntity : SnowflakePersistentAuditableEntity() {

    @Column(name = "user_id", nullable = false)
    var userId: Long = 0

    @Column(name = "position_id", nullable = false)
    var positionId: Long = 0

    @Column(name = "assigned_at", nullable = false)
    var assignedAt: Instant = Instant.now()

    @Column(name = "is_primary", nullable = false)
    var isPrimary: Boolean = false
}
