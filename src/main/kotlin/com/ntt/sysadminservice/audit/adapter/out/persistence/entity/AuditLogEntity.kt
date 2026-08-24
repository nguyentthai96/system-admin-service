package com.ntt.sysadminservice.audit.adapter.out.persistence.entity

import com.ntt.basecore.model.id.SnowflakePersistentAuditableEntity
import jakarta.persistence.*
import java.time.Instant

/**
 * Immutable audit log entity (FR-015).
 * No UPDATE/DELETE allowed — enforced by DB rules.
 * JSONB diff: {field: {old: X, new: Y}}
 */
@Entity
@Table(name = "audit_logs")
class AuditLogEntity : SnowflakePersistentAuditableEntity() {

    @Column(name = "user_id")
    var userId: Long? = null

    @Column(nullable = false, length = 100)
    lateinit var action: String

    @Column(name = "entity_type", length = 100)
    var entityType: String? = null

    @Column(name = "entity_id", length = 100)
    var entityId: String? = null

    @Column(name = "old_value", columnDefinition = "jsonb")
    var oldValue: String? = null

    @Column(name = "new_value", columnDefinition = "jsonb")
    var newValue: String? = null

    @Column(name = "ip_address", length = 45)
    var ipAddress: String? = null

    @Column(name = "user_agent", length = 500)
    var userAgent: String? = null

    @Column(length = 2000)
    var details: String? = null

    @Column(name = "occurred_at", nullable = false)
    var occurredAt: Instant = Instant.now()
}
