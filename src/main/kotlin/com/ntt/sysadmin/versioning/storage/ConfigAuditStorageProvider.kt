package com.ntt.sysadmin.versioning.storage

import com.ntt.sysadmin.versioning.storage.entity.ConfigMilestoneEntity
import com.ntt.sysadmin.versioning.storage.entity.ConfigSnapshotEntity
import com.ntt.sysadmin.versioning.storage.repository.ConfigMilestoneRepository
import com.ntt.sysadmin.versioning.storage.repository.ConfigSnapshotRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.domain.PageRequest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * Audit event model representing a single configuration change (FR-008, FR-013).
 */
data class ConfigAuditEvent(
    val id: String = UUID.randomUUID().toString(),
    val domainName: String,
    val naturalKey: String,
    val action: String, // CREATE, UPDATE, DELETE, ROLLBACK
    val previousStateJson: String? = null,
    val newStateJson: String? = null,
    val changedBy: String? = null,
    val changedAt: Instant = Instant.now()
)

/**
 * Pluggable Storage SPI for configuration audit events and milestone snapshots (FR-008, Non-functional Scalability).
 * Supports PostgreSQL JSONB out-of-the-box, with optional MongoDB extension.
 */
interface ConfigAuditStorageProvider {

    fun saveSnapshot(snapshot: ConfigSnapshotEntity): ConfigSnapshotEntity

    fun findSnapshotById(id: String): ConfigSnapshotEntity?

    fun findSnapshotsByMilestoneId(milestoneId: String): List<ConfigSnapshotEntity>

    fun findLatestSnapshot(domainName: String): ConfigSnapshotEntity?

    fun findSnapshotsByDomain(domainName: String, limit: Int): List<ConfigSnapshotEntity>

    fun saveMilestone(milestone: ConfigMilestoneEntity): ConfigMilestoneEntity

    fun findMilestoneById(id: String): ConfigMilestoneEntity?

    fun findAllMilestones(): List<ConfigMilestoneEntity>

    fun saveAuditBatch(events: List<ConfigAuditEvent>)

    fun findAuditHistory(domainName: String, naturalKey: String, limit: Int = 5): List<ConfigAuditEvent>
}

/**
 * Default PostgreSQL JSONB implementation of ConfigAuditStorageProvider.
 * Requires 0 additional infrastructure, using primary database with GIN indexing.
 */
@Component
@ConditionalOnProperty(name = ["app.config.audit.storage-type"], havingValue = "postgresql", matchIfMissing = true)
class PostgreSqlJsonbAuditStorageProvider(
    private val milestoneRepository: ConfigMilestoneRepository,
    private val snapshotRepository: ConfigSnapshotRepository,
    private val jdbcTemplate: JdbcTemplate
) : ConfigAuditStorageProvider {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun saveSnapshot(snapshot: ConfigSnapshotEntity): ConfigSnapshotEntity {
        return snapshotRepository.save(snapshot)
    }

    override fun findSnapshotById(id: String): ConfigSnapshotEntity? {
        return snapshotRepository.findById(id).orElse(null)
    }

    override fun findSnapshotsByMilestoneId(milestoneId: String): List<ConfigSnapshotEntity> {
        return snapshotRepository.findAllByMilestoneId(milestoneId)
    }

    override fun findLatestSnapshot(domainName: String): ConfigSnapshotEntity? {
        return snapshotRepository.findLatestByDomainName(domainName)
    }

    override fun findSnapshotsByDomain(domainName: String, limit: Int): List<ConfigSnapshotEntity> {
        val pageable = PageRequest.of(0, limit)
        return snapshotRepository.findAllByDomainNameOrderByCreatedAtDesc(domainName, pageable)
    }

    @Transactional
    override fun saveMilestone(milestone: ConfigMilestoneEntity): ConfigMilestoneEntity {
        return milestoneRepository.save(milestone)
    }

    override fun findMilestoneById(id: String): ConfigMilestoneEntity? {
        return milestoneRepository.findById(id).orElse(null)
    }

    override fun findAllMilestones(): List<ConfigMilestoneEntity> {
        return milestoneRepository.findAllByOrderByCreatedAtDesc()
    }

    @Transactional
    override fun saveAuditBatch(events: List<ConfigAuditEvent>) {
        if (events.isEmpty()) return
        log.debug("Persisting batch of {} audit events into PostgreSQL", events.size)

        val sql = """
            INSERT INTO domain_config_history (
                id, domain_id, config_key, old_value, new_value, version, changed_by, changed_at, active, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        jdbcTemplate.batchUpdate(
            sql,
            events,
            events.size
        ) { ps, event ->
            val numericId = System.currentTimeMillis() * 1000 + (Math.abs(event.id.hashCode()) % 1000)
            ps.setLong(1, numericId)
            ps.setLong(2, 0L) // Default 0 for system-wide configs
            ps.setString(3, "${event.domainName}:${event.naturalKey}")
            ps.setString(4, event.previousStateJson)
            ps.setString(5, event.newStateJson)
            ps.setInt(6, 1)
            ps.setString(7, event.changedBy ?: "system")
            ps.setTimestamp(8, Timestamp.from(event.changedAt))
            ps.setBoolean(9, true)
            ps.setTimestamp(10, Timestamp.from(event.changedAt))
        }
    }

    override fun findAuditHistory(domainName: String, naturalKey: String, limit: Int): List<ConfigAuditEvent> {
        val fullKey = "$domainName:$naturalKey"
        val sql = """
            SELECT id, config_key, old_value, new_value, changed_by, changed_at
            FROM domain_config_history
            WHERE config_key = ?
            ORDER BY changed_at DESC
            LIMIT ?
        """.trimIndent()

        return jdbcTemplate.query(sql, { rs, _ ->
            ConfigAuditEvent(
                id = rs.getLong("id").toString(),
                domainName = domainName,
                naturalKey = naturalKey,
                action = "UPDATE",
                previousStateJson = rs.getString("old_value"),
                newStateJson = rs.getString("new_value"),
                changedBy = rs.getString("changed_by"),
                changedAt = rs.getTimestamp("changed_at").toInstant()
            )
        }, fullKey, limit)
    }
}
