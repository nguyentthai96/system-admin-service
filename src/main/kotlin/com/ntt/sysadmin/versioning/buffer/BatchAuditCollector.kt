package com.ntt.sysadmin.versioning.buffer

import com.ntt.sysadmin.versioning.storage.ConfigAuditEvent
import com.ntt.sysadmin.versioning.storage.ConfigAuditStorageProvider
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.DisposableBean
import org.springframework.context.SmartLifecycle
import org.springframework.stereotype.Component
import java.util.ArrayList
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * High-performance in-memory micro-batch buffer for audit logs (FR-013).
 *
 * Batches audit writes (100 items or 500ms) to reduce DB I/O overhead.
 * Implements SmartLifecycle with high phase priority to flush all buffered
 * events safely before Spring closes the DataSource.
 */
@Component
class BatchAuditCollector(
    private val storageProvider: ConfigAuditStorageProvider
) : SmartLifecycle, DisposableBean {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        const val BATCH_SIZE = 100
        const val FLUSH_INTERVAL_MS = 500L
        const val MAX_CAPACITY = 20_000
    }

    private val queue = LinkedBlockingQueue<ConfigAuditEvent>(MAX_CAPACITY)
    private val isRunning = AtomicBoolean(false)
    private var scheduler: ScheduledExecutorService? = null

    fun enqueue(event: ConfigAuditEvent): Boolean {
        val offered = queue.offer(event)
        if (!offered) {
            log.warn("BatchAuditCollector queue full! Falling back to synchronous storage persist.")
            try {
                storageProvider.saveAuditBatch(listOf(event))
            } catch (ex: Exception) {
                log.error("Failed to synchronously write dropped audit event", ex)
            }
            return false
        }

        if (queue.size >= BATCH_SIZE) {
            triggerAsyncFlush()
        }
        return true
    }

    @Synchronized
    fun flush(): Int {
        if (queue.isEmpty()) return 0

        val batch = ArrayList<ConfigAuditEvent>(BATCH_SIZE)
        queue.drainTo(batch, BATCH_SIZE)

        if (batch.isNotEmpty()) {
            try {
                storageProvider.saveAuditBatch(batch)
                log.debug("Successfully flushed {} audit events to storage", batch.size)
            } catch (ex: Exception) {
                log.error("Error flushing audit event batch to storage", ex)
            }
        }
        return batch.size
    }

    @Synchronized
    fun flushAll(): Int {
        var totalFlushed = 0
        while (queue.isNotEmpty()) {
            val batch = ArrayList<ConfigAuditEvent>(BATCH_SIZE)
            queue.drainTo(batch, BATCH_SIZE)
            if (batch.isNotEmpty()) {
                try {
                    storageProvider.saveAuditBatch(batch)
                    totalFlushed += batch.size
                } catch (ex: Exception) {
                    log.error("Error during flushAll batch persist", ex)
                }
            }
        }
        log.info("FlushAll completed: {} buffered events saved", totalFlushed)
        return totalFlushed
    }

    private fun triggerAsyncFlush() {
        scheduler?.execute {
            flush()
        }
    }

    override fun start() {
        if (isRunning.compareAndSet(false, true)) {
            val threadFactory = ThreadFactory { r ->
                val thread = Thread(r, "batch-audit-collector")
                thread.isDaemon = true
                thread
            }
            scheduler = Executors.newSingleThreadScheduledExecutor(threadFactory)
            scheduler?.scheduleWithFixedDelay(
                { flush() },
                FLUSH_INTERVAL_MS,
                FLUSH_INTERVAL_MS,
                TimeUnit.MILLISECONDS
            )
            log.info("BatchAuditCollector started (batchSize={}, flushIntervalMs={})", BATCH_SIZE, FLUSH_INTERVAL_MS)
        }
    }

    override fun stop() {
        if (isRunning.compareAndSet(true, false)) {
            log.info("Stopping BatchAuditCollector, flushing all pending events...")
            try {
                flushAll()
            } finally {
                scheduler?.shutdown()
                try {
                    if (scheduler?.awaitTermination(5, TimeUnit.SECONDS) == false) {
                        scheduler?.shutdownNow()
                    }
                } catch (e: InterruptedException) {
                    scheduler?.shutdownNow()
                    Thread.currentThread().interrupt()
                }
                log.info("BatchAuditCollector stopped cleanly.")
            }
        }
    }

    override fun isRunning(): Boolean = isRunning.get()

    override fun isAutoStartup(): Boolean = true

    /**
     * High phase value ensures this component stops BEFORE the DataSource bean is closed.
     */
    override fun getPhase(): Int = 10_000

    override fun destroy() {
        stop()
    }
}
