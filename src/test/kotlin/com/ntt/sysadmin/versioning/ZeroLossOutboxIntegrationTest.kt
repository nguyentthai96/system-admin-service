package com.ntt.sysadmin.versioning

import com.ntt.sysadmin.versioning.buffer.BatchAuditCollector
import com.ntt.sysadmin.versioning.listener.ConfigDomainChangedEvent
import com.ntt.sysadmin.versioning.listener.ConfigDomainEventListener
import com.ntt.sysadmin.versioning.storage.ConfigAuditEvent
import com.ntt.sysadmin.versioning.storage.ConfigAuditStorageProvider
import com.ntt.sysadmin.versioning.storage.entity.ConfigMilestoneEntity
import com.ntt.sysadmin.versioning.storage.entity.ConfigSnapshotEntity
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/**
 * Integration test verifying non-blocking event flow and zero-loss audit batching (FR-011, FR-012, FR-013).
 */
class ZeroLossOutboxIntegrationTest {

    private class RecordingAuditStorageProvider : ConfigAuditStorageProvider {
        val persistedBatches = mutableListOf<List<ConfigAuditEvent>>()

        override fun saveSnapshot(snapshot: ConfigSnapshotEntity): ConfigSnapshotEntity = snapshot
        override fun findSnapshotById(id: String): ConfigSnapshotEntity? = null
        override fun findSnapshotsByMilestoneId(milestoneId: String): List<ConfigSnapshotEntity> = emptyList()
        override fun findLatestSnapshot(domainName: String): ConfigSnapshotEntity? = null
        override fun findSnapshotsByDomain(domainName: String, limit: Int): List<ConfigSnapshotEntity> = emptyList()
        override fun saveMilestone(milestone: ConfigMilestoneEntity): ConfigMilestoneEntity = milestone
        override fun findMilestoneById(id: String): ConfigMilestoneEntity? = null
        override fun findAllMilestones(): List<ConfigMilestoneEntity> = emptyList()

        override fun saveAuditBatch(events: List<ConfigAuditEvent>) {
            persistedBatches.add(ArrayList(events))
        }

        override fun findAuditHistory(domainName: String, naturalKey: String, limit: Int): List<ConfigAuditEvent> =
            emptyList()
    }

    @Test
    fun `event listener should forward outbox domain event into BatchAuditCollector without loss`() {
        val storage = RecordingAuditStorageProvider()
        val collector = BatchAuditCollector(storage)
        collector.start()

        val listener = ConfigDomainEventListener(collector)

        val event = ConfigDomainChangedEvent(
            eventId = UUID.randomUUID().toString(),
            domainName = "SYSTEM_CONFIG",
            naturalKey = "app.max.users",
            action = "UPDATE",
            previousStateJson = "{\"value\":\"500\"}",
            newStateJson = "{\"value\":\"1000\"}",
            changedBy = "admin-user",
            changedAt = Instant.now()
        )

        listener.onConfigDomainChanged(event)

        // Force flush of the buffer
        val flushed = collector.flush()
        assertEquals(1, flushed)

        assertEquals(1, storage.persistedBatches.size)
        val recorded = storage.persistedBatches[0][0]
        assertEquals("SYSTEM_CONFIG", recorded.domainName)
        assertEquals("app.max.users", recorded.naturalKey)
        assertEquals("UPDATE", recorded.action)
        assertEquals("admin-user", recorded.changedBy)

        collector.stop()
    }

    @Test
    fun `BatchAuditCollector graceful shutdown flushAll should drain all remaining events`() {
        val storage = RecordingAuditStorageProvider()
        val collector = BatchAuditCollector(storage)
        collector.start()

        // Enqueue 15 events into in-memory queue
        for (i in 1..15) {
            collector.enqueue(
                ConfigAuditEvent(
                    domainName = "I18N_MESSAGE",
                    naturalKey = "msg.key.$i",
                    action = "CREATE",
                    changedBy = "system"
                )
            )
        }

        // Simulate Spring application shutdown hook calling stop()
        collector.stop()

        val totalPersisted = storage.persistedBatches.sumOf { it.size }
        assertEquals(15, totalPersisted, "All buffered events must be safely drained and persisted during shutdown")
    }
}
