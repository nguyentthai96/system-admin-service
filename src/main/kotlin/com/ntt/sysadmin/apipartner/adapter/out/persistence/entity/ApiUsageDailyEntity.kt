package com.ntt.sysadmin.apipartner.adapter.out.persistence.entity

import jakarta.persistence.*
import java.time.Instant
import java.time.LocalDate

/**
 * API usage tracking per key per day (FR-012).
 */
@Entity
@Table(name = "api_usage_daily", uniqueConstraints = [
    UniqueConstraint(columnNames = ["api_key_id", "usage_date"])
])
class ApiUsageDailyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(name = "api_key_id", nullable = false)
    var apiKeyId: Long = 0

    @Column(name = "usage_date", nullable = false)
    var usageDate: LocalDate = LocalDate.now()

    @Column(name = "request_count", nullable = false)
    var requestCount: Long = 0

    @Column(name = "error_count", nullable = false)
    var errorCount: Long = 0

    @Column(name = "avg_latency_ms")
    var avgLatencyMs: Int = 0

    @Column(name = "quota_used", nullable = false)
    var quotaUsed: Long = 0

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
}
