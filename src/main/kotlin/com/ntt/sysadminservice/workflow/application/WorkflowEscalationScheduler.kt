package com.ntt.sysadminservice.workflow.application

import com.ntt.sysadminservice.workflow.adapter.out.persistence.entity.WorkflowStepEntity
import jakarta.persistence.EntityManager
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * Workflow escalation scheduler (FR-013).
 * Runs every 5 minutes, checks pending steps for timeout, escalates if exceeded.
 */
@Component
class WorkflowEscalationScheduler(
    private val entityManager: EntityManager,
    private val workflowEngine: WorkflowEngine
) {

    private val log = LoggerFactory.getLogger(WorkflowEscalationScheduler::class.java)

    /**
     * Check pending workflow steps for escalation timeout.
     * Runs every 5 minutes (300000ms).
     */
    @Scheduled(fixedRate = 300000)
    @Transactional
    fun checkEscalations() {
        val now = Instant.now()

        val pendingSteps = entityManager
            .createQuery(
                """
                SELECT s FROM WorkflowStepEntity s 
                WHERE s.status = 'PENDING' 
                AND s.escalated = false 
                AND s.active = true
                """.trimIndent(),
                WorkflowStepEntity::class.java
            )
            .resultList

        var escalatedCount = 0
        pendingSteps.forEach { step ->
            val createdAt = step.createdAt ?: return@forEach
            val timeoutSeconds = step.escalationTimeout.toLong()
            val deadline = createdAt.plusSeconds(timeoutSeconds)

            if (now.isAfter(deadline)) {
                try {
                    workflowEngine.escalateStep(step)
                    escalatedCount++
                } catch (e: Exception) {
                    log.error("Failed to escalate step {}: {}", step.id, e.message)
                }
            }
        }

        if (escalatedCount > 0) {
            log.info("Workflow escalation: {} step(s) escalated", escalatedCount)
        }
    }
}
