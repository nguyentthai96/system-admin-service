package com.ntt.sysadminservice.config.adapter.out.persistence.entity

import com.ntt.basecore.model.id.SnowflakePersistentAuditableEntity
import jakarta.persistence.*

/**
 * Feature flag entity — boolean + percentage rollout + user segment targeting (FR-014).
 */
@Entity
@Table(
    name = "feature_flags",
    uniqueConstraints = [UniqueConstraint(columnNames = ["domain_id", "flag_key"])]
)
class FeatureFlagEntity : SnowflakePersistentAuditableEntity() {

    @Column(name = "domain_id", nullable = false)
    var domainId: Long = 0

    @Column(name = "flag_key", nullable = false, length = 100)
    lateinit var flagKey: String

    @Column(nullable = false)
    var enabled: Boolean = false

    /** Percentage rollout (0-100). 0 = disabled, 100 = all users. */
    @Column(name = "rollout_pct", nullable = false)
    var rolloutPct: Int = 0

    /** JSON array of user segment IDs for targeted rollout. */
    @Column(name = "user_segments", columnDefinition = "jsonb")
    var userSegments: String = "[]"

    @Column(length = 500)
    var description: String? = null
}
