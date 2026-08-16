package com.ntt.sysadmin.apipartner.adapter.out.persistence.entity

import jakarta.persistence.*
import java.time.Instant

/**
 * Rate limit audit log entity (FR-012).
 */
@Entity
@Table(name = "rate_limit_audit")
class RateLimitAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(name = "api_key_id", nullable = false)
    var apiKeyId: Long = 0

    @Column(name = "endpoint")
    var endpoint: String? = null

    @Column(name = "client_ip")
    var clientIp: String? = null

    @Column(name = "rejected_at", nullable = false)
    var rejectedAt: Instant = Instant.now()

    @Column(name = "limit_type", nullable = false)
    var limitType: String = "PER_SECOND"

    @Column(name = "current_count")
    var currentCount: Long? = null

    @Column(name = "limit_value")
    var limitValue: Long? = null
}
