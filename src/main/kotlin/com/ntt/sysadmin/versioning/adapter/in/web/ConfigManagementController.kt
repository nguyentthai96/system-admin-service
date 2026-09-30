package com.ntt.sysadmin.versioning.adapter.`in`.web

import com.fasterxml.jackson.databind.ObjectMapper
import com.ntt.basecore.domain.file.ExportConfig
import com.ntt.basecore.domain.file.ExportFormat
import com.ntt.basecore.autoconfigure.file.export.*
import com.ntt.basecore.autoconfigure.file.imports.ImportStrategyMode
import com.ntt.basecore.autoconfigure.file.imports.RelationalImportCoordinator
import com.ntt.sysadmin.versioning.adapter.`in`.web.dto.*
import com.ntt.sysadmin.versioning.application.ConfigSnapshotManager
import com.ntt.sysadmin.versioning.domain.ConfigDomainRegistry
import com.ntt.sysadmin.versioning.domain.VersionedConfigDomain
import com.ntt.sysadminservice.shared.exception.ImportValidationException
import com.ntt.sysadminservice.shared.exception.SysAdminErrorCode
import com.ntt.sysadminservice.shared.exception.SysAdminException
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.io.InputStream
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Unified REST controller for Configuration Management System (FR-001 - FR-014).
 * Exposes milestone snapshots, live diff comparison, conflict-aware rollback,
 * and multi-format export/import capabilities.
 */
@RestController
@RequestMapping("/api/v1/configs")
class ConfigManagementController(
    private val snapshotManager: ConfigSnapshotManager,
    private val domainRegistry: ConfigDomainRegistry,
    private val importHandlerRegistry: com.ntt.sysadmin.versioning.domain.ImportHandlerRegistry,
    private val relationalImportCoordinator: RelationalImportCoordinator,
    private val metamodelExtractor: DynamicJpaMetamodelSheetExtractor?,
    private val objectMapper: ObjectMapper
) {

    private val log = LoggerFactory.getLogger(javaClass)

    // ==========================================
    // 1. Milestone & Snapshot Management (FR-009)
    // ==========================================

    @PostMapping("/milestones")
    fun createMilestone(
        @Valid @RequestBody request: CreateMilestoneRequest,
        @RequestHeader(value = "X-User-Id", required = false) userId: String?
    ): ResponseEntity<MilestoneResponse> {
        val milestone = snapshotManager.createMilestone(
            name = request.name,
            description = request.description,
            domainNames = request.domainNames,
            createdBy = userId
        )
        val snapshots = snapshotManager.getSnapshotsForMilestone(milestone.id).map {
            SnapshotSummaryResponse(it.id, it.domainName, it.checksumSha256, it.recordCount, it.createdAt)
        }
        return ResponseEntity.ok(
            MilestoneResponse(
                id = milestone.id,
                name = milestone.name,
                description = milestone.description,
                createdBy = milestone.createdBy,
                createdAt = milestone.createdAt,
                status = milestone.status,
                snapshots = snapshots
            )
        )
    }

    @GetMapping("/milestones")
    fun listMilestones(): ResponseEntity<List<MilestoneResponse>> {
        val list = snapshotManager.listMilestones().map { m ->
            val snapshots = snapshotManager.getSnapshotsForMilestone(m.id).map {
                SnapshotSummaryResponse(it.id, it.domainName, it.checksumSha256, it.recordCount, it.createdAt)
            }
            MilestoneResponse(m.id, m.name, m.description, m.createdBy, m.createdAt, m.status, snapshots)
        }
        return ResponseEntity.ok(list)
    }

    @GetMapping("/milestones/{id}")
    fun getMilestone(@PathVariable id: String): ResponseEntity<MilestoneResponse> {
        val milestone = snapshotManager.getMilestone(id)
            ?: throw SysAdminException(SysAdminErrorCode.NOT_FOUND, "Milestone not found: $id")
        val snapshots = snapshotManager.getSnapshotsForMilestone(milestone.id).map {
            SnapshotSummaryResponse(it.id, it.domainName, it.checksumSha256, it.recordCount, it.createdAt)
        }
        return ResponseEntity.ok(
            MilestoneResponse(
                milestone.id,
                milestone.name,
                milestone.description,
                milestone.createdBy,
                milestone.createdAt,
                milestone.status,
                snapshots
            )
        )
    }

    @GetMapping("/snapshots/{id}")
    fun getSnapshot(@PathVariable id: String): ResponseEntity<SnapshotSummaryResponse> {
        val snapshot = snapshotManager.getSnapshot(id)
            ?: throw SysAdminException(SysAdminErrorCode.SNAPSHOT_NOT_FOUND, "Snapshot not found: $id")
        return ResponseEntity.ok(
            SnapshotSummaryResponse(
                snapshot.id,
                snapshot.domainName,
                snapshot.checksumSha256,
                snapshot.recordCount,
                snapshot.createdAt
            )
        )
    }

    // ==========================================
    // 2. Diff & Rollback (FR-010, FR-014)
    // ==========================================

    @GetMapping("/domains/{domainName}/diff")
    fun getDiff(
        @PathVariable domainName: String,
        @RequestParam snapshotId: String
    ): ResponseEntity<ConfigDiffResponse> {
        val diff = snapshotManager.compareDiff(domainName, snapshotId)
        val response = ConfigDiffResponse(
            domainName = diff.domainName,
            snapshotId = diff.snapshotId,
            snapshotCreatedAt = diff.snapshotCreatedAt,
            addedCount = diff.addedCount,
            removedCount = diff.removedCount,
            modifiedCount = diff.modifiedCount,
            entities = diff.entities.map { entityDiff ->
                EntityDiffResponse(
                    naturalKey = entityDiff.naturalKey,
                    diffType = entityDiff.diffType.name,
                    fieldDiffs = entityDiff.fieldDiffs.map { FieldDiffResponse(it.fieldName, it.oldValue, it.newValue) }
                )
            }
        )
        return ResponseEntity.ok(response)
    }

    @PostMapping("/snapshots/{snapshotId}/rollback")
    fun rollback(
        @PathVariable snapshotId: String,
        @RequestBody(required = false) request: RollbackRequest?,
        @RequestHeader(value = "X-User-Id", required = false) userId: String?
    ): ResponseEntity<RollbackResponse> {
        val force = request?.forceOverwrite ?: false
        val result = snapshotManager.rollback(snapshotId, force, userId)
        return ResponseEntity.ok(
            RollbackResponse(
                domainName = result.domainName,
                snapshotId = result.snapshotId,
                milestoneId = result.milestoneId,
                recordsRestored = result.recordsRestored,
                conflictsOverridden = result.conflictsOverridden,
                executedAt = result.executedAt
            )
        )
    }

    // ==========================================
    // 3. Export & Import (FR-002, FR-003, FR-004, FR-005, FR-006, FR-007)
    // ==========================================

    @GetMapping("/domains/{domainName}/export")
    fun exportDomain(
        @PathVariable domainName: String,
        @RequestParam(defaultValue = "json") format: String,
        response: HttpServletResponse
    ) {
        val domain = domainRegistry.getDomain(domainName)
            ?: throw SysAdminException(SysAdminErrorCode.NOT_FOUND, "Domain not registered: $domainName")

        val state = domain.fetchCurrentState()
        val filename = "${domainName.lowercase()}_export.${format.lowercase()}"
        val encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8.toString()).replace("+", "%20")

        when (format.lowercase()) {
            "json" -> {
                response.contentType = MediaType.APPLICATION_JSON_VALUE
                response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$encodedFilename\"")
                val strategy = SimpleJsonExportStrategy<Any>(objectMapper)
                val exportConfig = ExportConfig(columns = emptyList())
                @Suppress("UNCHECKED_CAST")
                strategy.export(state.stream() as java.util.stream.Stream<Any>, exportConfig, response.outputStream)
            }
            else -> {
                response.contentType = MediaType.APPLICATION_JSON_VALUE
                response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$encodedFilename\"")
                objectMapper.writeValue(response.outputStream, state)
            }
        }
    }

    @GetMapping("/export/all")
    fun exportAllDomainsMultiSheet(response: HttpServletResponse) {
        val domains = domainRegistry.getAllDomains()
        val sheetDefinitions = mutableListOf<SheetExportDefinition<*>>()

        for (domain in domains) {
            val template = domain.exportTemplate()
            if (template != null) {
                val state = domain.fetchCurrentState()
                @Suppress("UNCHECKED_CAST")
                sheetDefinitions.add(
                    SheetExportDefinition(
                        sheetName = domain.domainName,
                        columns = template.columns,
                        dataSupplier = { state.stream() as java.util.stream.Stream<Any> }
                    )
                )
            }
        }

        if (sheetDefinitions.isEmpty()) {
            throw SysAdminException(SysAdminErrorCode.GENERAL_ERROR, "No domains configured for multi-sheet export")
        }

        val filename = "all_configurations_backup.xlsx"
        val encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8.toString()).replace("+", "%20")

        response.contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$encodedFilename\"")

        val strategy = MultiSheetExcelExportStrategy()
        val exportTemplate = object : MultiSheetExportTemplate {
            override val templateId: String = "all-configurations-backup"
            override fun sheets(filter: Map<String, Any>?): List<SheetExportDefinition<*>> = sheetDefinitions
        }
        strategy.export(exportTemplate, null, response.outputStream)
    }

    @PostMapping("/import")
    fun importData(
        @RequestParam("file") file: MultipartFile,
        @RequestParam(defaultValue = "UPSERT_MERGE") mode: String,
        @RequestParam(required = false) checksum: String?,
        @RequestHeader(value = "X-User-Id", required = false) userId: String?
    ): ResponseEntity<ImportSummaryResponse> {
        if (file.isEmpty) {
            throw ImportValidationException("Uploaded file is empty")
        }

        val executionMode = try {
            ImportStrategyMode.valueOf(mode.uppercase())
        } catch (ex: IllegalArgumentException) {
            throw ImportValidationException(
                "Invalid import mode: $mode. Allowed: TRUNCATE_AND_LOAD, DELETE_AND_INSERT, UPSERT_MERGE, PATCH_VALUES"
            )
        }

        // Validate SHA-256 checksum if provided
        if (checksum != null) {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val fileHash = digest.digest(file.bytes).joinToString("") { "%02x".format(it) }
            if (!fileHash.equals(checksum, ignoreCase = true)) {
                throw ImportValidationException(
                    "Checksum mismatch: expected=$checksum, actual=$fileHash"
                )
            }
        }

        log.info("Processing configuration import: filename={}, size={}, mode={}", file.originalFilename, file.size, executionMode)

        // Parse JSON import payload
        val importPayload: Map<String, List<Any>> = try {
            @Suppress("UNCHECKED_CAST")
            objectMapper.readValue(file.inputStream, Map::class.java) as Map<String, List<Any>>
        } catch (e: Exception) {
            throw ImportValidationException("Failed to parse import file: ${e.message}")
        }

        // Execute import via coordinator
        relationalImportCoordinator.executeImport(
            domain = "config-import",
            mode = executionMode,
            recordsByTable = importPayload,
            executedBy = userId
        )

        return ResponseEntity.ok(
            ImportSummaryResponse(
                totalRecordsProcessed = importPayload.values.sumOf { it.size },
                tablesProcessed = importPayload.keys.toList(),
                mode = executionMode.name,
                success = true,
                message = "Configuration file '${file.originalFilename}' successfully imported in mode $executionMode."
            )
        )
    }
}
