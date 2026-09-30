package com.ntt.sysadmin.versioning.listener

import com.ntt.sysadmin.versioning.buffer.BatchAuditCollector
import com.ntt.sysadmin.versioning.storage.ConfigAuditEvent
import org.slf4j.LoggerFactory
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

/**
 * Domain event published when any configuration aggregate is mutated (FR-011, FR-012).
 *
 * Automatically intercepted by Spring Modulith JPA Event Publication Registry
 * and recorded into the EVENT_PUBLICATION outbox table for zero-loss durability.
 */
data class ConfigDomainChangedEvent(
    val eventId: String = UUID.randomUUID().toString(),
    val domainName: String,
    val naturalKey: String,
    val action: String, // CREATE, UPDATE, DELETE, ROLLBACK
    val previousStateJson: String? = null,
    val newStateJson: String? = null,
    val changedBy: String? = null,
    val changedAt: Instant = Instant.now()
)

/**
 * Asynchronous event listener triggered after transaction commit (FR-011).
 *
 * Dispatches audit events into BatchAuditCollector for micro-batching.
 * On server crash/restart, Spring Modulith resubmits uncompleted events from EVENT_PUBLICATION.
 */
@Component
class ConfigDomainEventListener(
    private val batchAuditCollector: BatchAuditCollector
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @ApplicationModuleListener
    fun onConfigDomainChanged(event: ConfigDomainChangedEvent) {
        log.debug(
            "Processing outbox event: domain={}, key={}, action={}, eventId={}",
            event.domainName, event.naturalKey, event.action, event.eventId
        )

        val auditEvent = ConfigAuditEvent(
            id = event.eventId,
            domainName = event.domainName,
            naturalKey = event.naturalKey,
            action = event.action,
            previousStateJson = event.previousStateJson,
            newStateJson = event.newStateJson,
            changedBy = event.changedBy,
            changedAt = event.changedAt
        )

        batchAuditCollector.enqueue(auditEvent)
    }
}
