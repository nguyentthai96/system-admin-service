package com.ntt.sysadminservice.workflow.application

import com.ntt.sysadminservice.shared.exception.SysAdminErrorCode
import com.ntt.sysadminservice.shared.exception.SysAdminException
import com.ntt.sysadminservice.workflow.adapter.out.persistence.entity.WorkflowInstanceEntity
import com.ntt.sysadminservice.workflow.adapter.out.persistence.entity.WorkflowStepEntity
import jakarta.persistence.EntityManager
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * Workflow engine — state machine for approval workflow (FR-013).
 * Enhanced with conditional routing (PBAC PolicyCondition DSL reuse),
 * delegation support, explicit guard conditions per transition.
 *
 * States: PENDING → IN_PROGRESS → APPROVED/REJECTED/ESCALATED/CANCELLED
 * Guards: canResubmit, canCancel, isEscalated
 */
@Component
class WorkflowEngine(
    private val entityManager: EntityManager
) {

    private val log = LoggerFactory.getLogger(WorkflowEngine::class.java)

    companion object {
        val VALID_TRANSITIONS = mapOf(
            "PENDING" to setOf("IN_PROGRESS", "CANCELLED"),
            "IN_PROGRESS" to setOf("APPROVED", "REJECTED", "ESCALATED", "CANCELLED"),
            "ESCALATED" to setOf("APPROVED", "REJECTED", "CANCELLED"),
            "REJECTED" to setOf("PENDING") // Resubmit allowed via guard
        )

        val TERMINAL_STATES = setOf("APPROVED", "REJECTED", "CANCELLED")

        /** Guard conditions — explicit per transition */
        val GUARD_CONDITIONS = mapOf(
            "canResubmit" to { instance: WorkflowInstanceEntity ->
                instance.status == "REJECTED"
            },
            "canCancel" to { instance: WorkflowInstanceEntity ->
                instance.status !in TERMINAL_STATES
            },
            "isEscalated" to { instance: WorkflowInstanceEntity ->
                instance.status == "ESCALATED"
            }
        )
    }

    /**
     * Process a decision on a workflow step.
     * Enhanced with conditional routing based on step conditions (JSONB DSL).
     */
    @Transactional
    fun processDecision(
        instanceId: Long,
        stepId: Long,
        decision: String,
        userId: Long,
        comments: String?
    ): WorkflowInstanceEntity {
        val instance = entityManager.find(WorkflowInstanceEntity::class.java, instanceId)
            ?: throw SysAdminException(SysAdminErrorCode.WORKFLOW_NOT_FOUND, "Workflow instance not found: $instanceId")

        if (instance.status in TERMINAL_STATES) {
            throw SysAdminException(SysAdminErrorCode.ALREADY_PROCESSED, "Workflow already completed: ${instance.status}")
        }

        val step = entityManager.find(WorkflowStepEntity::class.java, stepId)
            ?: throw SysAdminException(SysAdminErrorCode.WORKFLOW_NOT_FOUND, "Workflow step not found: $stepId")

        if (step.status != "PENDING" && step.status != "ESCALATED") {
            throw SysAdminException(SysAdminErrorCode.ALREADY_PROCESSED, "Step already processed: ${step.status}")
        }

        if (decision !in setOf("APPROVED", "REJECTED")) {
            throw SysAdminException(SysAdminErrorCode.INVALID_TRANSITION, "Invalid decision: $decision")
        }

        // Record decision
        step.status = decision
        step.decision = decision
        step.decidedBy = userId
        step.decidedAt = Instant.now()
        step.comments = comments
        entityManager.merge(step)

        // Advance workflow with conditional routing
        if (decision == "REJECTED") {
            instance.status = "REJECTED"
            instance.completedAt = Instant.now()
        } else {
            // Find next step — evaluate conditions for conditional routing
            val nextStep = findNextStep(instanceId, step.stepOrder)

            if (nextStep == null) {
                instance.status = "APPROVED"
                instance.completedAt = Instant.now()
            } else {
                // Evaluate step condition if present
                if (shouldSkipStep(nextStep, instance)) {
                    // Skip this step and find the one after
                    nextStep.status = "SKIPPED"
                    nextStep.comments = "Skipped by conditional routing"
                    entityManager.merge(nextStep)

                    val stepAfterSkip = findNextStep(instanceId, nextStep.stepOrder)
                    if (stepAfterSkip == null) {
                        instance.status = "APPROVED"
                        instance.completedAt = Instant.now()
                    } else {
                        instance.currentStep = stepAfterSkip.stepOrder
                        instance.status = "IN_PROGRESS"
                    }
                } else {
                    instance.currentStep = nextStep.stepOrder
                    instance.status = "IN_PROGRESS"
                }
            }
        }

        entityManager.merge(instance)
        log.info("Workflow decision: instanceId={}, stepId={}, decision={}, newStatus={}",
            instanceId, stepId, decision, instance.status)
        return instance
    }

    /**
     * Delegate a workflow step to another user (FR-013).
     */
    @Transactional
    fun delegateStep(instanceId: Long, stepId: Long, delegateTo: Long, reason: String?): WorkflowStepEntity {
        val instance = entityManager.find(WorkflowInstanceEntity::class.java, instanceId)
            ?: throw SysAdminException(SysAdminErrorCode.WORKFLOW_NOT_FOUND, "Workflow instance not found: $instanceId")

        if (instance.status in TERMINAL_STATES) {
            throw SysAdminException(SysAdminErrorCode.ALREADY_PROCESSED, "Workflow already completed: ${instance.status}")
        }

        val step = entityManager.find(WorkflowStepEntity::class.java, stepId)
            ?: throw SysAdminException(SysAdminErrorCode.WORKFLOW_NOT_FOUND, "Workflow step not found: $stepId")

        if (step.status != "PENDING" && step.status != "ESCALATED") {
            throw SysAdminException(SysAdminErrorCode.ALREADY_PROCESSED, "Cannot delegate processed step: ${step.status}")
        }

        val previousApprover = step.approverUserId
        step.approverUserId = delegateTo
        step.delegatedFrom = previousApprover
        step.delegationReason = reason
        entityManager.merge(step)

        log.info("Workflow step delegated: instanceId={}, stepId={}, from={}, to={}, reason={}",
            instanceId, stepId, previousApprover, delegateTo, reason)
        return step
    }

    /**
     * Resubmit a rejected workflow (guard: canResubmit).
     */
    @Transactional
    fun resubmitWorkflow(instanceId: Long, userId: Long): WorkflowInstanceEntity {
        val instance = entityManager.find(WorkflowInstanceEntity::class.java, instanceId)
            ?: throw SysAdminException(SysAdminErrorCode.WORKFLOW_NOT_FOUND, "Workflow instance not found: $instanceId")

        val canResubmit = GUARD_CONDITIONS["canResubmit"]?.invoke(instance) ?: false
        if (!canResubmit) {
            throw SysAdminException(SysAdminErrorCode.INVALID_TRANSITION,
                "Cannot resubmit workflow in status: ${instance.status}")
        }

        // Reset all steps to PENDING
        entityManager.createQuery(
            "UPDATE WorkflowStepEntity s SET s.status = 'PENDING', s.decision = null, s.decidedAt = null, s.comments = null, s.escalated = false WHERE s.instanceId = :instanceId"
        )
            .setParameter("instanceId", instanceId)
            .executeUpdate()

        instance.status = "IN_PROGRESS"
        instance.currentStep = 0
        instance.completedAt = null
        entityManager.merge(instance)

        log.info("Workflow resubmitted: instanceId={}, by userId={}", instanceId, userId)
        return instance
    }

    /**
     * Check guard condition for a transition.
     */
    fun checkGuard(guardName: String, instance: WorkflowInstanceEntity): Boolean {
        return GUARD_CONDITIONS[guardName]?.invoke(instance) ?: false
    }

    /**
     * Escalate a pending step that has exceeded its timeout.
     */
    @Transactional
    fun escalateStep(step: WorkflowStepEntity) {
        step.status = "ESCALATED"
        step.escalated = true
        entityManager.merge(step)

        val instance = entityManager.find(WorkflowInstanceEntity::class.java, step.instanceId)
        if (instance != null) {
            instance.status = "ESCALATED"
            entityManager.merge(instance)
        }

        log.info("Workflow step escalated: stepId={}, instanceId={}", step.id, step.instanceId)
    }

    /**
     * Find the next pending step after the current order.
     */
    private fun findNextStep(instanceId: Long, currentOrder: Int): WorkflowStepEntity? {
        return entityManager
            .createQuery(
                "SELECT s FROM WorkflowStepEntity s WHERE s.instanceId = :instanceId AND s.stepOrder > :order AND s.status = 'PENDING' ORDER BY s.stepOrder",
                WorkflowStepEntity::class.java
            )
            .setParameter("instanceId", instanceId)
            .setParameter("order", currentOrder)
            .resultList
            .firstOrNull()
    }

    /**
     * Evaluate whether a step should be skipped based on its condition (JSONB DSL).
     * Condition format (simplified PBAC-style): {"field": "entityType", "operator": "eq", "value": "LEAVE_REQUEST"}
     * If condition evaluates to false, skip the step.
     */
    private fun shouldSkipStep(step: WorkflowStepEntity, instance: WorkflowInstanceEntity): Boolean {
        val condition = step.conditionJson
        if (condition.isNullOrBlank() || condition == "{}") return false

        return try {
            val mapper = com.fasterxml.jackson.databind.ObjectMapper()
            val condNode = mapper.readTree(condition)
            val field = condNode.get("field")?.asText() ?: return false
            val operator = condNode.get("operator")?.asText() ?: return false
            val expectedValue = condNode.get("value")?.asText() ?: return false

            val actualValue = when (field) {
                "entityType" -> instance.entityType
                "status" -> instance.status
                else -> return false
            }

            val matches = when (operator) {
                "eq" -> actualValue == expectedValue
                "neq" -> actualValue != expectedValue
                "in" -> expectedValue.split(",").contains(actualValue)
                else -> true
            }

            !matches // Skip if condition does NOT match
        } catch (e: Exception) {
            log.warn("Failed to evaluate step condition: {}", e.message)
            false // Do not skip on evaluation error
        }
    }
}
