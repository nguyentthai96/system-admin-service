package com.ntt.sysadmin.apipartner.adapter.`in`.web.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

/**
 * DTOs for API Partner Management (FR-012).
 */

data class RegisterPartnerRequest(
    @field:NotNull val domainId: Long,
    @field:NotBlank val partnerName: String,
    @field:NotBlank val partnerCode: String,
    val contactEmail: String? = null,
    val contactPhone: String? = null,
    val description: String? = null,
    val subscriptionPlanId: Long? = null
)

data class PartnerResponse(
    val id: Long,
    val partnerName: String,
    val partnerCode: String,
    val contactEmail: String?,
    val status: String,
    val subscriptionPlanId: Long?,
    val createdAt: Long
)

data class GenerateApiKeyRequest(
    @field:NotNull val partnerId: Long,
    val name: String? = null,
    val environment: String = "PRODUCTION",
    val scopes: List<String> = emptyList(),
    val rateLimitPerSecond: Int = 10,
    val rateLimitPerMinute: Int = 600,
    val rateLimitPerDay: Long = 10000,
    val quotaMonthly: Long = 300000,
    val ipWhitelist: List<String> = emptyList(),
    val expiresInDays: Long? = null
)

/** Response shown ONLY ONCE when key is generated (BR-API-01) */
data class ApiKeyGeneratedResponse(
    val keyId: Long,
    val apiKey: String, // Plain text — shown only this once!
    val keyPrefix: String,
    val name: String?,
    val message: String = "IMPORTANT: Save this API key now. It will NOT be shown again."
)

data class ApiKeyResponse(
    val id: Long,
    val keyPrefix: String,
    val name: String?,
    val rateLimitPerSecond: Int,
    val rateLimitPerMinute: Int,
    val rateLimitPerDay: Long,
    val quotaMonthly: Long,
    val status: String,
    val lastUsedAt: Long?,
    val expiresAt: Long?
)

data class ApiUsageSummary(
    val partnerId: Long,
    val totalRequests: Long,
    val periodStart: Long,
    val periodEnd: Long,
    val statusCodeBreakdown: Map<Int, Long>
)
