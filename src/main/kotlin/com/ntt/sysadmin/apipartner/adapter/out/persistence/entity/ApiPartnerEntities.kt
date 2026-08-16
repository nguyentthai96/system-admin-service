package com.ntt.sysadmin.apipartner.adapter.out.persistence.entity

import com.ntt.basecore.model.id.SnowflakePersistentAuditableEntity
import jakarta.persistence.*

/**
 * Subscription plan — rate limit tiers for API partners (FR-012).
 */
@Entity
@Table(name = "subscription_plans")
class SubscriptionPlanEntity : SnowflakePersistentAuditableEntity() {

    @Column(nullable = false, unique = true, length = 50)
    lateinit var code: String

    @Column(nullable = false, length = 200)
    lateinit var name: String

    @Column(length = 500)
    var description: String? = null

    @Column(name = "max_requests_per_day", nullable = false)
    var maxRequestsPerDay: Long = 10000

    @Column(name = "max_requests_per_month", nullable = false)
    var maxRequestsPerMonth: Long = 300000

    @Column(name = "rate_limit_per_second", nullable = false)
    var rateLimitPerSecond: Int = 10

    @Column(name = "allowed_apis_json", columnDefinition = "TEXT")
    var allowedApisJson: String? = null

    @Column(name = "price_monthly")
    var priceMonthly: java.math.BigDecimal? = null

    @Column(nullable = false, length = 20)
    var status: String = "ACTIVE"
}

/**
 * API Partner registration (FR-012).
 */
@Entity
@Table(name = "api_partners")
class ApiPartnerEntity : SnowflakePersistentAuditableEntity() {

    @Column(name = "domain_id", nullable = false)
    var domainId: Long = 0

    @Column(name = "partner_name", nullable = false, length = 200)
    lateinit var partnerName: String

    @Column(name = "partner_code", nullable = false, unique = true, length = 50)
    lateinit var partnerCode: String

    @Column(name = "contact_email", length = 255)
    var contactEmail: String? = null

    @Column(name = "contact_phone", length = 50)
    var contactPhone: String? = null

    @Column(length = 500)
    var description: String? = null

    @Column(name = "subscription_plan_id")
    var subscriptionPlanId: Long? = null

    @Column(nullable = false, length = 20)
    var status: String = "ACTIVE"
}

/**
 * API Key (FR-012).
 * BR-API-01: Key shown only once at generation, stored as SHA-256 hash.
 * BR-API-04: Prefix format ntt_pk_ (prod) / ntt_sk_ (sandbox).
 */
@Entity
@Table(name = "api_keys")
class ApiKeyEntity : SnowflakePersistentAuditableEntity() {

    @Column(name = "partner_id", nullable = false)
    var partnerId: Long = 0

    @Column(name = "key_prefix", nullable = false, length = 20)
    lateinit var keyPrefix: String

    @Column(name = "key_hash", nullable = false, unique = true, length = 128)
    lateinit var keyHash: String

    @Column(length = 200)
    var name: String? = null

    @Column(name = "scopes_json", columnDefinition = "TEXT")
    var scopesJson: String? = null

    @Column(name = "rate_limit_per_second", nullable = false)
    var rateLimitPerSecond: Int = 10

    @Column(name = "rate_limit_per_minute", nullable = false)
    var rateLimitPerMinute: Int = 600

    @Column(name = "rate_limit_per_day", nullable = false)
    var rateLimitPerDay: Long = 10000

    @Column(name = "quota_monthly", nullable = false)
    var quotaMonthly: Long = 300000

    @Column(name = "ip_whitelist_json", columnDefinition = "TEXT")
    var ipWhitelistJson: String? = null

    @Column(name = "expires_at")
    var expiresAt: Long? = null

    @Column(name = "last_used_at")
    var lastUsedAt: Long? = null

    @Column(nullable = false, length = 20)
    var status: String = "ACTIVE"

    companion object {
        const val PREFIX_PRODUCTION = "ntt_pk_"
        const val PREFIX_SANDBOX = "ntt_sk_"
        const val STATUS_ACTIVE = "ACTIVE"
        const val STATUS_SUSPENDED = "SUSPENDED"
        const val STATUS_REVOKED = "REVOKED"
    }
}

/**
 * API usage log entry (FR-012).
 */
@Entity
@Table(name = "api_usage_logs")
class ApiUsageLogEntity : SnowflakePersistentAuditableEntity() {

    @Column(name = "partner_id", nullable = false)
    var partnerId: Long = 0

    @Column(name = "api_key_id", nullable = false)
    var apiKeyId: Long = 0

    @Column(nullable = false, length = 500)
    lateinit var endpoint: String

    @Column(nullable = false, length = 10)
    lateinit var method: String

    @Column(name = "status_code", nullable = false)
    var statusCode: Int = 0

    @Column(name = "response_time_ms")
    var responseTimeMs: Int? = null

    @Column(name = "ip_address", length = 45)
    var ipAddress: String? = null

    @Column(name = "request_at", nullable = false)
    var requestAt: Long = System.currentTimeMillis()
}
