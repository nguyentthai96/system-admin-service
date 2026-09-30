package com.ntt.sysadmin.versioning.application

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.ntt.sysadmin.versioning.domain.ConfigDomainRegistry
import com.ntt.sysadmin.versioning.domain.VersionedConfigDomain
import com.ntt.sysadmin.versioning.listener.ConfigDomainChangedEvent
import com.ntt.sysadmin.versioning.storage.ConfigAuditStorageProvider
import com.ntt.sysadmin.versioning.storage.entity.ConfigMilestoneEntity
import com.ntt.sysadmin.versioning.storage.entity.ConfigSnapshotEntity
import com.ntt.sysadminservice.shared.exception.RollbackConflictException
import com.ntt.sysadminservice.shared.exception.SnapshotNotFoundException
import com.ntt.sysadminservice.shared.exception.SysAdminErrorCode
import com.ntt.sysadminservice.shared.exception.SysAdminException
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

enum class DiffType {
    ADDED, REMOVED, MODIFIED
}

data class FieldDiff(
    val fieldName: String,
    val oldValue: Any?,
    val newValue: Any?
)

data class EntityDiff(
    val naturalKey: String,
    val diffType: DiffType,
    val fieldDiffs: List<FieldDiff> = emptyList()
)

data class DomainDiffResult(
    val domainName: String,
    val snapshotId: String,
    val snapshotCreatedAt: Instant,
    val addedCount: Int,
    val removedCount: Int,
    val modifiedCount: Int,
    val entities: List<EntityDiff>
)

data class RollbackExecutionResult(
    val domainName: String,
    val snapshotId: String,
    val milestoneId: String?,
    val recordsRestored: Int,
    val conflictsOverridden: List<String> = emptyList(),
    val executedAt: Instant = Instant.now()
)

/**
 * Coordinates configuration versioning, milestones, point-in-time diffing,
 * and conflict-aware rollback (FR-009, FR-010, FR-014).
 */
@Service
class ConfigSnapshotManager(
    private val domainRegistry: ConfigDomainRegistry,
    private val storageProvider: ConfigAuditStorageProvider,
    private val objectMapper: ObjectMapper,
    private val eventPublisher: ApplicationEventPublisher
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Creates a named milestone grouping snapshots across all or selected configuration domains (FR-009).
     */
    @Transactional
    fun createMilestone(
        name: String,
        description: String? = null,
        domainNames: List<String>? = null,
        createdBy: String? = null
    ): ConfigMilestoneEntity {
        log.info("Creating configuration milestone '{}' by {}", name, createdBy)

        val milestone = ConfigMilestoneEntity().apply {
            this.id = UUID.randomUUID().toString()
            this.name = name
            this.description = description
            this.createdBy = createdBy
            this.createdAt = Instant.now()
            this.status = "ACTIVE"
        }
        val savedMilestone = storageProvider.saveMilestone(milestone)

        val targetDomains: List<VersionedConfigDomain<*>> = if (!domainNames.isNullOrEmpty()) {
            domainNames.mapNotNull { domainRegistry.getDomain(it) }
        } else {
            domainRegistry.getAllDomains()
        }

        for (domain in targetDomains) {
            createDomainSnapshotInternal(domain, savedMilestone.id)
        }

        return savedMilestone
    }

    /**
     * Creates a standalone point-in-time snapshot for a specific domain.
     */
    @Transactional
    fun createDomainSnapshot(domainName: String, milestoneId: String? = null): ConfigSnapshotEntity {
        val domain = domainRegistry.getDomain(domainName)
            ?: throw SysAdminException(SysAdminErrorCode.NOT_FOUND, "Domain not registered: $domainName")
        return createDomainSnapshotInternal(domain, milestoneId)
    }

    private fun createDomainSnapshotInternal(
        domain: VersionedConfigDomain<*>,
        milestoneId: String?
    ): ConfigSnapshotEntity {
        val currentState = domain.fetchCurrentState()
        val jsonPayload = objectMapper.writeValueAsString(currentState)
        val checksum = calculateSha256(jsonPayload)

        val snapshot = ConfigSnapshotEntity().apply {
            this.id = UUID.randomUUID().toString()
            this.milestoneId = milestoneId
            this.domainName = domain.domainName.uppercase()
            this.statePayload = jsonPayload
            this.checksumSha256 = checksum
            this.recordCount = currentState.size
            this.createdAt = Instant.now()
        }

        val saved = storageProvider.saveSnapshot(snapshot)
        log.info(
            "Snapshot captured for domain {}: {} records, checksum={}",
            domain.domainName, currentState.size, checksum
        )
        return saved
    }

    /**
     * Compares the live current state of a domain against a historical snapshot (FR-010).
     */
    fun compareDiff(domainName: String, snapshotId: String): DomainDiffResult {
        val snapshot = storageProvider.findSnapshotById(snapshotId)
            ?: throw SnapshotNotFoundException(snapshotId)

        val domain = domainRegistry.getDomain(domainName)
            ?: throw SysAdminException(SysAdminErrorCode.NOT_FOUND, "Domain not registered: $domainName")

        return computeDomainDiff(domain, snapshot)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <E : Any> computeDomainDiff(
        domain: VersionedConfigDomain<E>,
        snapshot: ConfigSnapshotEntity
    ): DomainDiffResult {
        val snapshotType = objectMapper.typeFactory.constructCollectionType(List::class.java, domain.entityClass)
        val snapshotEntities: List<E> = objectMapper.readValue(snapshot.statePayload, snapshotType)
        val currentEntities: List<E> = domain.fetchCurrentState()

        val snapshotMap = snapshotEntities.associateBy { domain.naturalKeyExtractor(it) }
        val currentMap = currentEntities.associateBy { domain.naturalKeyExtractor(it) }

        val diffList = mutableListOf<EntityDiff>()
        var addedCount = 0
        var removedCount = 0
        var modifiedCount = 0

        // Check for records added currently (present now, absent in snapshot)
        for ((key, currentEntity) in currentMap) {
            if (!snapshotMap.containsKey(key)) {
                addedCount++
                diffList.add(EntityDiff(naturalKey = key, diffType = DiffType.ADDED))
            }
        }

        // Check for records removed currently (present in snapshot, absent now)
        for ((key, snapshotEntity) in snapshotMap) {
            if (!currentMap.containsKey(key)) {
                removedCount++
                diffList.add(EntityDiff(naturalKey = key, diffType = DiffType.REMOVED))
            } else {
                // Present in both: check for field-level modifications
                val currentEntity = currentMap[key]!!
                val currentJson = objectMapper.valueToTree<JsonNode>(currentEntity)
                val snapshotJson = objectMapper.valueToTree<JsonNode>(snapshotEntity)

                if (currentJson != snapshotJson) {
                    val fieldDiffs = extractFieldDiffs(snapshotJson, currentJson)
                    if (fieldDiffs.isNotEmpty()) {
                        modifiedCount++
                        diffList.add(
                            EntityDiff(
                                naturalKey = key,
                                diffType = DiffType.MODIFIED,
                                fieldDiffs = fieldDiffs
                            )
                        )
                    }
                }
            }
        }

        return DomainDiffResult(
            domainName = domain.domainName,
            snapshotId = snapshot.id,
            snapshotCreatedAt = snapshot.createdAt,
            addedCount = addedCount,
            removedCount = removedCount,
            modifiedCount = modifiedCount,
            entities = diffList
        )
    }

    private fun extractFieldDiffs(oldJson: JsonNode, newJson: JsonNode): List<FieldDiff> {
        val fieldDiffs = mutableListOf<FieldDiff>()
        val allFieldNames = (oldJson.fieldNames().asSequence() + newJson.fieldNames().asSequence()).toSet()

        for (fieldName in allFieldNames) {
            // Ignore audit timestamp fields in data comparison
            if (fieldName in setOf("updatedAt", "updated_at", "createdAt", "created_at", "version")) continue

            val oldVal = oldJson.get(fieldName)
            val newVal = newJson.get(fieldName)

            if (oldVal != newVal) {
                fieldDiffs.add(
                    FieldDiff(
                        fieldName = fieldName,
                        oldValue = oldVal?.asText(),
                        newValue = newVal?.asText()
                    )
                )
            }
        }
        return fieldDiffs
    }

    /**
     * Conflict-aware rollback of a domain state to a historical snapshot (FR-010, FR-014).
     *
     * Detects if any live records have been modified post-snapshot.
     * If conflicts exist and forceOverwrite is false, throws RollbackConflictException.
     */
    @Transactional
    fun rollback(
        snapshotId: String,
        forceOverwrite: Boolean = false,
        changedBy: String? = null
    ): RollbackExecutionResult {
        val snapshot = storageProvider.findSnapshotById(snapshotId)
            ?: throw SnapshotNotFoundException(snapshotId)

        val domain = domainRegistry.getDomain(snapshot.domainName)
            ?: throw SysAdminException(SysAdminErrorCode.NOT_FOUND, "Domain not registered: ${snapshot.domainName}")

        return executeRollbackInternal(domain, snapshot, forceOverwrite, changedBy)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <E : Any> executeRollbackInternal(
        domain: VersionedConfigDomain<E>,
        snapshot: ConfigSnapshotEntity,
        forceOverwrite: Boolean,
        changedBy: String?
    ): RollbackExecutionResult {
        log.info(
            "Executing rollback for domain {} to snapshot {} (forceOverwrite={})",
            domain.domainName, snapshot.id, forceOverwrite
        )

        val snapshotEpoch = snapshot.createdAt.toEpochMilli()
        val currentEntities = domain.fetchCurrentState()
        val conflicts = mutableListOf<String>()

        for (entity in currentEntities) {
            val updatedAt = domain.getEntityUpdatedAt(entity)
            if (updatedAt != null && updatedAt > snapshotEpoch) {
                conflicts.add(domain.naturalKeyExtractor(entity))
            }
        }

        if (conflicts.isNotEmpty() && !forceOverwrite) {
            val preview = conflicts.take(5).joinToString(", ")
            val more = if (conflicts.size > 5) " (+${conflicts.size - 5} more)" else ""
            throw RollbackConflictException(
                "Rollback conflict detected! ${conflicts.size} record(s) modified post-snapshot: [$preview$more]. Supply forceOverwrite=true to force rollback.",
                conflicts
            )
        }

        // Apply domain rollback
        domain.applyRollbackState(snapshot.statePayload, forceOverwrite)

        // Publish event for audit / outbox
        eventPublisher.publishEvent(
            ConfigDomainChangedEvent(
                domainName = domain.domainName,
                naturalKey = "ALL_SNAPSHOT_${snapshot.id}",
                action = "ROLLBACK",
                newStateJson = snapshot.checksumSha256,
                changedBy = changedBy ?: "system"
            )
        )

        return RollbackExecutionResult(
            domainName = domain.domainName,
            snapshotId = snapshot.id,
            milestoneId = snapshot.milestoneId,
            recordsRestored = snapshot.recordCount,
            conflictsOverridden = if (forceOverwrite) conflicts else emptyList()
        )
    }

    fun getMilestone(id: String): ConfigMilestoneEntity? = storageProvider.findMilestoneById(id)

    fun listMilestones(): List<ConfigMilestoneEntity> = storageProvider.findAllMilestones()

    fun getSnapshotsForMilestone(milestoneId: String): List<ConfigSnapshotEntity> =
        storageProvider.findSnapshotsByMilestoneId(milestoneId)

    fun getSnapshot(id: String): ConfigSnapshotEntity? = storageProvider.findSnapshotById(id)

    fun getLatestSnapshot(domainName: String): ConfigSnapshotEntity? = storageProvider.findLatestSnapshot(domainName)

    fun listSnapshotsByDomain(domainName: String, limit: Int = 10): List<ConfigSnapshotEntity> =
        storageProvider.findSnapshotsByDomain(domainName, limit)

    private fun calculateSha256(data: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(data.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }
}
