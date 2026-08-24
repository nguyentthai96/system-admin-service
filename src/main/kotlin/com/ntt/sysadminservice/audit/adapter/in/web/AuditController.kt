package com.ntt.sysadminservice.audit.adapter.`in`.web

import com.fasterxml.jackson.databind.ObjectMapper
import com.ntt.sysadminservice.audit.adapter.out.persistence.entity.AuditLogEntity
import com.ntt.sysadminservice.audit.application.AuditService
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Audit controller — REST endpoints for audit log access (FR-015).
 * Enhanced with CSV/JSON export support and sensitive data masking.
 */
@RestController
@RequestMapping("/api/admin/audit-logs")
class AuditController(
    private val auditService: AuditService,
    private val objectMapper: ObjectMapper
) {

    @GetMapping
    fun searchAuditLogs(
        @RequestParam(required = false) userId: Long?,
        @RequestParam(required = false) action: String?,
        @RequestParam(required = false) entityType: String?,
        @RequestParam(required = false) from: Instant?,
        @RequestParam(required = false) to: Instant?,
        pageable: Pageable
    ): ResponseEntity<Page<AuditLogEntity>> {
        return ResponseEntity.ok(auditService.searchAuditLogs(userId, action, entityType, from, to, pageable))
    }

    /**
     * Export audit logs as JSON (FR-015).
     */
    @GetMapping("/export", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun exportAuditLogsJson(
        @RequestParam(required = false) from: Instant?,
        @RequestParam(required = false) to: Instant?,
        @RequestParam(defaultValue = "10000") limit: Int,
        @RequestParam(defaultValue = "json") format: String
    ): ResponseEntity<Any> {
        val logs = auditService.exportAuditLogs(from, to, limit)

        return when (format.lowercase()) {
            "csv" -> {
                val csv = buildCsvExport(logs)
                ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=audit_logs.csv")
                    .contentType(MediaType.parseMediaType("text/csv"))
                    .body(csv)
            }
            else -> {
                ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=audit_logs.json")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(logs)
            }
        }
    }

    /**
     * Build CSV export string from audit logs.
     */
    private fun buildCsvExport(logs: List<AuditLogEntity>): String {
        val sb = StringBuilder()
        val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME.withZone(ZoneOffset.UTC)

        // Header
        sb.appendLine("id,user_id,action,entity_type,entity_id,ip_address,occurred_at,details")

        // Data rows
        logs.forEach { log ->
            val escapedDetails = (log.details ?: "").replace("\"", "\"\"")
            sb.appendLine(
                "${log.id},${log.userId ?: ""},\"${log.action}\",\"${log.entityType ?: ""}\",\"${log.entityId ?: ""}\"," +
                "\"${log.ipAddress ?: ""}\",\"${dateFormatter.format(log.occurredAt)}\",\"$escapedDetails\""
            )
        }

        return sb.toString()
    }
}
