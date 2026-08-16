package com.ntt.sysadmin.tenant.adapter.out.persistence.entity

import jakarta.persistence.*
import java.time.Instant

/**
 * Domain config change history entity (FR-016).
 * Stores snapshots of config for versioning/rollback.
 */
@Entity
@Table(name = "domain_config_history")
class DomainConfigHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(name = "domain_id", nullable = false)
    var domainId: Long = 0

    @Column(name = "config_snapshot", nullable = false, columnDefinition = "JSONB")
    var configSnapshot: String = "{}"

    @Column(name = "changed_by")
    var changedBy: String? = null

    @Column(name = "changed_at", nullable = false)
    var changedAt: Instant = Instant.now()

    @Column(name = "change_reason")
    var changeReason: String? = null

    @Column(name = "version", nullable = false)
    var version: Int = 1
}
