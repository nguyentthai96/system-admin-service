package com.ntt.sysadmin.apipartner.adapter.`in`.web

import com.ntt.sysadmin.apipartner.application.ApiUsageService
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

/**
 * API Usage & Monitoring Controller (FR-012).
 *
 * Provides System Administrator endpoints for:
 * - Usage statistics per partner
 * - Quota status
 * - Rate limit audit logs
 */
@RestController
@RequestMapping("/api/admin/partners")
class ApiUsageController(
    private val apiUsageService: ApiUsageService
) {

    /**
     * Get usage statistics for a partner.
     */
    @GetMapping("/{partnerId}/usage")
    fun getUsageStatistics(
        @PathVariable partnerId: Long,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate?,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate?
    ): ResponseEntity<Map<String, Any>> {
        val start = startDate ?: LocalDate.now().minusDays(30)
        val end = endDate ?: LocalDate.now()

        val stats = apiUsageService.getUsageStatistics(partnerId, start, end)
        return ResponseEntity.ok(stats)
    }

    /**
     * Get quota status for an API key.
     */
    @GetMapping("/api-keys/{apiKeyId}/quota")
    fun getQuotaStatus(@PathVariable apiKeyId: Long): ResponseEntity<Map<String, Long>> {
        val quota = apiUsageService.getRemainingQuota(apiKeyId)
        return ResponseEntity.ok(quota)
    }

    /**
     * Get rate limit audit for an API key.
     */
    @GetMapping("/api-keys/{apiKeyId}/rate-limit-audit")
    fun getRateLimitAudit(
        @PathVariable apiKeyId: Long,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<Map<String, Any>> {
        val auditPage = apiUsageService.getRateLimitAudit(apiKeyId, page, size)
        val records = auditPage.content.map { audit ->
            mapOf(
                "id" to audit.id,
                "endpoint" to audit.endpoint,
                "clientIp" to audit.clientIp,
                "limitType" to audit.limitType,
                "currentCount" to audit.currentCount,
                "limitValue" to audit.limitValue,
                "rejectedAt" to audit.rejectedAt.toString()
            )
        }

        return ResponseEntity.ok(mapOf(
            "content" to records,
            "page" to page,
            "size" to size,
            "totalElements" to auditPage.totalElements,
            "totalPages" to auditPage.totalPages
        ))
    }
}
