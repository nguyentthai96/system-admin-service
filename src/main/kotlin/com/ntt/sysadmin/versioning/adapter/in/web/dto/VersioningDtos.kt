package com.ntt.sysadmin.versioning.adapter.`in`.web.dto

import jakarta.validation.constraints.NotBlank
import java.time.Instant

/**
 * Request payload to create a configuration milestone grouping domain snapshots (FR-009).
 */
data class CreateMilestoneRequest(
    @field:NotBlank(message = "Milestone name must not be blank")
    val name: String,
    val description: String? = null,
    val domainNames: List<String>? = null
)

/**
 * Request payload for rollback execution with optional force override (FR-010, FR-014).
 */
data class RollbackRequest(
    val forceOverwrite: Boolean = false
)

/**
 * DTO representing milestone metadata and contained snapshot summaries (FR-009).
 */
data class MilestoneResponse(
    val id: String,
    val name: String,
    val description: String?,
    val createdBy: String?,
    val createdAt: Instant,
    val status: String,
    val snapshots: List<SnapshotSummaryResponse> = emptyList()
)

/**
 * DTO representing snapshot metadata.
 */
data class SnapshotSummaryResponse(
    val id: String,
    val domainName: String,
    val checksumSha256: String,
    val recordCount: Int,
    val createdAt: Instant
)

/**
 * DTO detailing field-level differences for a specific entity (FR-010).
 */
data class FieldDiffResponse(
    val fieldName: String,
    val oldValue: Any?,
    val newValue: Any?
)

/**
 * DTO detailing diff status of a single record/aggregate (FR-010).
 */
data class EntityDiffResponse(
    val naturalKey: String,
    val diffType: String,
    val fieldDiffs: List<FieldDiffResponse> = emptyList()
)

/**
 * Response payload summarizing point-in-time diff against a snapshot (FR-010).
 */
data class ConfigDiffResponse(
    val domainName: String,
    val snapshotId: String,
    val snapshotCreatedAt: Instant,
    val addedCount: Int,
    val removedCount: Int,
    val modifiedCount: Int,
    val entities: List<EntityDiffResponse>
)

/**
 * Response payload after executing a snapshot rollback (FR-010, FR-014).
 */
data class RollbackResponse(
    val domainName: String,
    val snapshotId: String,
    val milestoneId: String?,
    val recordsRestored: Int,
    val conflictsOverridden: List<String> = emptyList(),
    val executedAt: Instant
)

/**
 * Response payload summarizing import execution (FR-005, FR-006, FR-007).
 */
data class ImportSummaryResponse(
    val totalRecordsProcessed: Int,
    val tablesProcessed: List<String>,
    val mode: String,
    val success: Boolean,
    val message: String
)
