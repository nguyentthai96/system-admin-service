package com.ntt.sysadminservice.apipartner.adapter.`in`.web

import com.ntt.sysadminservice.apipartner.application.ApiPartnerService
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * API Usage controller — usage dashboard, API key lifecycle, IP whitelist, rate limiting (FR-012).
 * Endpoints:
 *   - GET /api/admin/api-usage — usage stats
 *   - POST /api/admin/api-partners/{id}/keys — generate key (show-once)
 *   - POST /api/admin/api-partners/{id}/keys/{keyId}/rotate — rotate key
 *   - DELETE /api/admin/api-partners/keys/{keyId} — revoke key
 *   - PUT /api/admin/api-partners/{id}/ip-whitelist — update IP whitelist
 *   - PUT /api/admin/api-partners/{id}/rate-limit — configure rate limit
 */
@RestController
@RequestMapping("/api/admin")
class ApiUsageController(
    private val apiPartnerService: ApiPartnerService
) {

    // ─── Usage Dashboard ────────────────────────────────────────

    @GetMapping("/api-usage")
    fun getUsageStatistics(@RequestParam partnerId: Long): ResponseEntity<Map<String, Any>> {
        return ResponseEntity.ok(apiPartnerService.getUsageStatistics(partnerId))
    }

    // ─── API Key Lifecycle ──────────────────────────────────────

    /**
     * Generate a new API key (show-once pattern).
     * The plain key is returned ONLY in this response — store it securely.
     */
    @PostMapping("/api-partners/{id}/keys")
    fun generateApiKey(
        @PathVariable id: Long,
        @Valid @RequestBody request: GenerateKeyRequest
    ): ResponseEntity<Map<String, Any>> {
        val result = apiPartnerService.generateApiKey(id, request.keyType)
        return ResponseEntity.ok(result)
    }

    /**
     * Rotate an API key — revokes old, generates new.
     */
    @PostMapping("/api-partners/{id}/keys/{keyId}/rotate")
    fun rotateApiKey(
        @PathVariable id: Long,
        @PathVariable keyId: Long
    ): ResponseEntity<Map<String, Any>> {
        val result = apiPartnerService.rotateApiKey(id, keyId)
        return ResponseEntity.ok(result)
    }

    /**
     * Revoke an API key permanently.
     */
    @DeleteMapping("/api-partners/keys/{keyId}")
    fun revokeApiKey(@PathVariable keyId: Long): ResponseEntity<Map<String, Boolean>> {
        apiPartnerService.revokeApiKey(keyId)
        return ResponseEntity.ok(mapOf("success" to true))
    }

    // ─── IP Whitelist ───────────────────────────────────────────

    @PutMapping("/api-partners/{id}/ip-whitelist")
    fun updateIpWhitelist(
        @PathVariable id: Long,
        @RequestBody request: IpWhitelistRequest
    ): ResponseEntity<Map<String, Boolean>> {
        apiPartnerService.updateIpWhitelist(id, request.ipAddresses)
        return ResponseEntity.ok(mapOf("success" to true))
    }

    // ─── Rate Limiting ──────────────────────────────────────────

    /**
     * Configure rate limit for a partner.
     * Pushes config to Redis for Gateway Bucket4j ProxyManager.
     */
    @PutMapping("/api-partners/{id}/rate-limit")
    fun configureRateLimit(
        @PathVariable id: Long,
        @Valid @RequestBody request: RateLimitConfigRequest
    ): ResponseEntity<Map<String, Boolean>> {
        apiPartnerService.configureRateLimit(id, request.requestsPerMinute, request.burstCapacity)
        return ResponseEntity.ok(mapOf("success" to true))
    }
}

data class GenerateKeyRequest(
    val keyType: String = "sk" // "pk" (publishable) or "sk" (secret)
)

data class IpWhitelistRequest(
    val ipAddresses: List<String>
)

data class RateLimitConfigRequest(
    val requestsPerMinute: Int = 100,
    val burstCapacity: Int = 20
)
