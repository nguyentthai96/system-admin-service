package com.ntt.sysadmin.apipartner.adapter.`in`.web

import com.ntt.sysadmin.apipartner.ApiKeyService
import com.ntt.sysadmin.apipartner.adapter.`in`.web.dto.*
import com.ntt.sysadmin.apipartner.application.ApiPartnerService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * REST Controller for API Partner Management (FR-012).
 */
@RestController
@RequestMapping("/api/admin")
class ApiPartnerController(
    private val partnerService: ApiPartnerService,
    private val apiKeyService: ApiKeyService
) {

    // ===============================
    // Partner CRUD
    // ===============================

    @PostMapping("/api-partners")
    fun registerPartner(
        @Valid @RequestBody request: RegisterPartnerRequest
    ): ResponseEntity<PartnerResponse> {
        val partner = partnerService.registerPartner(request)
        return ResponseEntity.ok(PartnerResponse(
            id = partner.id!!,
            partnerName = partner.partnerName,
            partnerCode = partner.partnerCode,
            contactEmail = partner.contactEmail,
            status = partner.status,
            subscriptionPlanId = partner.subscriptionPlanId,
            createdAt = partner.createdAt?.toEpochMilli() ?: System.currentTimeMillis()
        ))
    }

    @GetMapping("/api-partners")
    fun listPartners(
        @RequestParam(required = false) domainId: Long?
    ): ResponseEntity<List<PartnerResponse>> {
        val partners = if (domainId != null) {
            partnerService.getPartnersByDomain(domainId)
        } else {
            partnerService.getAllPartners()
        }
        return ResponseEntity.ok(partners)
    }

    @GetMapping("/api-partners/{partnerId}")
    fun getPartner(@PathVariable partnerId: Long): ResponseEntity<PartnerResponse> {
        val partner = partnerService.getPartner(partnerId)
        return ResponseEntity.ok(PartnerResponse(
            id = partner.id!!,
            partnerName = partner.partnerName,
            partnerCode = partner.partnerCode,
            contactEmail = partner.contactEmail,
            status = partner.status,
            subscriptionPlanId = partner.subscriptionPlanId,
            createdAt = partner.createdAt?.toEpochMilli() ?: System.currentTimeMillis()
        ))
    }

    // ===============================
    // API Key Management
    // ===============================

    /**
     * Generate new API key.
     * BR-API-01: Raw key shown ONLY in this response.
     */
    @PostMapping("/api-keys")
    fun generateApiKey(
        @Valid @RequestBody request: GenerateApiKeyRequest
    ): ResponseEntity<ApiKeyGeneratedResponse> {
        val (entity, rawKey) = apiKeyService.generateApiKey(
            partnerId = request.partnerId,
            name = request.name,
            environment = request.environment,
            rateLimitPerSecond = request.rateLimitPerSecond,
            rateLimitPerMinute = request.rateLimitPerMinute,
            rateLimitPerDay = request.rateLimitPerDay,
            quotaMonthly = request.quotaMonthly,
            scopes = request.scopes,
            ipWhitelist = request.ipWhitelist,
            expiresInDays = request.expiresInDays
        )

        return ResponseEntity.ok(ApiKeyGeneratedResponse(
            keyId = entity.id!!,
            apiKey = rawKey,
            keyPrefix = entity.keyPrefix,
            name = entity.name
        ))
    }

    /**
     * List API keys for a partner (metadata only — no raw keys).
     */
    @GetMapping("/api-keys")
    fun listApiKeys(@RequestParam partnerId: Long): ResponseEntity<List<ApiKeyResponse>> {
        val keys = apiKeyService.getPartnerApiKeys(partnerId)
        return ResponseEntity.ok(keys.map {
            ApiKeyResponse(
                id = it.id!!,
                keyPrefix = it.keyPrefix,
                name = it.name,
                rateLimitPerSecond = it.rateLimitPerSecond,
                rateLimitPerMinute = it.rateLimitPerMinute,
                rateLimitPerDay = it.rateLimitPerDay,
                quotaMonthly = it.quotaMonthly,
                status = it.status,
                lastUsedAt = it.lastUsedAt,
                expiresAt = it.expiresAt
            )
        })
    }

    /**
     * Rotate API key — revoke old, generate new.
     */
    @PutMapping("/api-keys/{id}/rotate")
    fun rotateApiKey(@PathVariable id: Long): ResponseEntity<ApiKeyGeneratedResponse> {
        val (entity, rawKey) = apiKeyService.rotateApiKey(id)
        return ResponseEntity.ok(ApiKeyGeneratedResponse(
            keyId = entity.id!!,
            apiKey = rawKey,
            keyPrefix = entity.keyPrefix,
            name = entity.name
        ))
    }

    /**
     * Revoke an API key.
     */
    @DeleteMapping("/api-keys/{id}")
    fun revokeApiKey(@PathVariable id: Long): ResponseEntity<Map<String, Any>> {
        apiKeyService.revokeApiKey(id)
        return ResponseEntity.ok(mapOf("id" to id, "status" to "REVOKED"))
    }

    /**
     * Suspend an API key.
     */
    @PutMapping("/api-keys/{id}/suspend")
    fun suspendApiKey(@PathVariable id: Long): ResponseEntity<Map<String, Any>> {
        apiKeyService.suspendApiKey(id)
        return ResponseEntity.ok(mapOf("id" to id, "status" to "SUSPENDED"))
    }
}
