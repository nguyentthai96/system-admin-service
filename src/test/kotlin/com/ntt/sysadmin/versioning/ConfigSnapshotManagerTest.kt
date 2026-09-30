package com.ntt.sysadmin.versioning

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ntt.sysadmin.versioning.application.ConfigSnapshotManager
import com.ntt.sysadmin.versioning.application.DiffType
import com.ntt.sysadmin.versioning.domain.ConfigDomainRegistry
import com.ntt.sysadmin.versioning.domain.VersionedConfigDomain
import com.ntt.sysadmin.versioning.storage.ConfigAuditEvent
import com.ntt.sysadmin.versioning.storage.ConfigAuditStorageProvider
import com.ntt.sysadmin.versioning.storage.entity.ConfigMilestoneEntity
import com.ntt.sysadmin.versioning.storage.entity.ConfigSnapshotEntity
import com.ntt.sysadminservice.shared.exception.RollbackConflictException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.context.ApplicationEventPublisher
import java.time.Instant

class ConfigSnapshotManagerTest {

    private val objectMapper: ObjectMapper = jacksonObjectMapper()
    private val eventPublisher: ApplicationEventPublisher = mock(ApplicationEventPublisher::class.java)

    data class DummyConfig(
        val key: String,
        var value: String,
        var updatedAt: Long = System.currentTimeMillis()
    )

    private class InMemoryStorageProvider : ConfigAuditStorageProvider {
        val milestones = mutableMapOf<String, ConfigMilestoneEntity>()
        val snapshots = mutableMapOf<String, ConfigSnapshotEntity>()

        override fun saveSnapshot(snapshot: ConfigSnapshotEntity): ConfigSnapshotEntity {
            snapshots[snapshot.id] = snapshot
            return snapshot
        }

        override fun findSnapshotById(id: String): ConfigSnapshotEntity? = snapshots[id]

        override fun findSnapshotsByMilestoneId(milestoneId: String): List<ConfigSnapshotEntity> =
            snapshots.values.filter { it.milestoneId == milestoneId }

        override fun findLatestSnapshot(domainName: String): ConfigSnapshotEntity? =
            snapshots.values.filter { it.domainName == domainName }.maxByOrNull { it.createdAt }

        override fun findSnapshotsByDomain(domainName: String, limit: Int): List<ConfigSnapshotEntity> =
            snapshots.values.filter { it.domainName == domainName }.sortedByDescending { it.createdAt }.take(limit)

        override fun saveMilestone(milestone: ConfigMilestoneEntity): ConfigMilestoneEntity {
            milestones[milestone.id] = milestone
            return milestone
        }

        override fun findMilestoneById(id: String): ConfigMilestoneEntity? = milestones[id]

        override fun findAllMilestones(): List<ConfigMilestoneEntity> =
            milestones.values.sortedByDescending { it.createdAt }

        override fun saveAuditBatch(events: List<ConfigAuditEvent>) {}

        override fun findAuditHistory(domainName: String, naturalKey: String, limit: Int): List<ConfigAuditEvent> =
            emptyList()
    }

    private class MockVersionedDomain(
        val items: MutableList<DummyConfig> = mutableListOf()
    ) : VersionedConfigDomain<DummyConfig> {
        override val domainName: String = "TEST_DOMAIN"
        override val entityClass: Class<DummyConfig> = DummyConfig::class.java
        override val naturalKeyExtractor: (DummyConfig) -> String = { it.key }
        override fun getEntityUpdatedAt(entity: DummyConfig): Long = entity.updatedAt
        override fun fetchCurrentState(): List<DummyConfig> = items.toList()

        var lastAppliedPayload: String? = null
        override fun applyRollbackState(snapshotPayload: String, forceOverwrite: Boolean) {
            lastAppliedPayload = snapshotPayload
        }
    }

    private lateinit var storage: InMemoryStorageProvider
    private lateinit var mockDomain: MockVersionedDomain
    private lateinit var registry: ConfigDomainRegistry
    private lateinit var manager: ConfigSnapshotManager

    @BeforeEach
    fun setUp() {
        storage = InMemoryStorageProvider()
        mockDomain = MockVersionedDomain(
            mutableListOf(
                DummyConfig("feature.alpha", "true", 1000L),
                DummyConfig("feature.beta", "false", 1000L)
            )
        )
        registry = ConfigDomainRegistry(listOf(mockDomain))
        manager = ConfigSnapshotManager(registry, storage, objectMapper, eventPublisher)
    }

    @Test
    fun `createMilestone should capture snapshots for all registered domains`() {
        val milestone = manager.createMilestone("v1.0.0", "Release 1 milestone")

        assertNotNull(milestone.id)
        assertEquals("v1.0.0", milestone.name)
        val snapshots = storage.findSnapshotsByMilestoneId(milestone.id)
        assertEquals(1, snapshots.size)
        assertEquals("TEST_DOMAIN", snapshots[0].domainName)
        assertEquals(2, snapshots[0].recordCount)
        assertTrue(snapshots[0].checksumSha256.isNotBlank())
    }

    @Test
    fun `rollback should succeed when no records modified post-snapshot`() {
        // Snapshot created at instant with epoch 2000L
        val snapshot = manager.createDomainSnapshot("TEST_DOMAIN")

        val result = manager.rollback(snapshot.id, forceOverwrite = false)

        assertEquals("TEST_DOMAIN", result.domainName)
        assertEquals(2, result.recordsRestored)
        assertTrue(result.conflictsOverridden.isEmpty())
        assertNotNull(mockDomain.lastAppliedPayload)
    }

    @Test
    fun `rollback should detect conflicts and throw 409 when entity updated post-snapshot`() {
        // Snapshot created at instant T0
        val snapshot = manager.createDomainSnapshot("TEST_DOMAIN")

        // Modify an entity at T1 (post-snapshot)
        val newerTimestamp = snapshot.createdAt.toEpochMilli() + 5000L
        mockDomain.items[0].value = "modified_post_snapshot"
        mockDomain.items[0].updatedAt = newerTimestamp

        // Attempt rollback without force
        val exception = assertThrows(RollbackConflictException::class.java) {
            manager.rollback(snapshot.id, forceOverwrite = false)
        }

        assertTrue(exception.conflicts.contains("feature.alpha"))
        assertTrue(exception.message.contains("Rollback conflict"))
    }

    @Test
    fun `rollback with forceOverwrite true should bypass conflict warning`() {
        val snapshot = manager.createDomainSnapshot("TEST_DOMAIN")

        val newerTimestamp = snapshot.createdAt.toEpochMilli() + 5000L
        mockDomain.items[0].updatedAt = newerTimestamp

        val result = manager.rollback(snapshot.id, forceOverwrite = true)

        assertEquals("TEST_DOMAIN", result.domainName)
        assertEquals(1, result.conflictsOverridden.size)
        assertEquals("feature.alpha", result.conflictsOverridden[0])
    }

    @Test
    fun `compareDiff should identify added, removed, and modified entities`() {
        val snapshot = manager.createDomainSnapshot("TEST_DOMAIN")

        // Current state mutations:
        // 1. Modify alpha
        mockDomain.items[0].value = "updated_value"
        // 2. Remove beta
        mockDomain.items.removeAt(1)
        // 3. Add gamma
        mockDomain.items.add(DummyConfig("feature.gamma", "new", 3000L))

        val diff = manager.compareDiff("TEST_DOMAIN", snapshot.id)

        assertEquals(1, diff.addedCount) // gamma added
        assertEquals(1, diff.removedCount) // beta removed
        assertEquals(1, diff.modifiedCount) // alpha modified

        val modifiedEntity = diff.entities.find { it.naturalKey == "feature.alpha" }
        assertNotNull(modifiedEntity)
        assertEquals(DiffType.MODIFIED, modifiedEntity?.diffType)
        val fieldDiff = modifiedEntity?.fieldDiffs?.find { it.fieldName == "value" }
        assertEquals("true", fieldDiff?.oldValue)
        assertEquals("updated_value", fieldDiff?.newValue)
    }
}
