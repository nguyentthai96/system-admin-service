package com.ntt.sysadminservice.workflow.application

import com.ntt.sysadminservice.shared.exception.SysAdminErrorCode
import com.ntt.sysadminservice.shared.exception.SysAdminException
import com.ntt.sysadminservice.workflow.adapter.out.persistence.entity.WorkflowDefinitionEntity
import com.ntt.sysadminservice.workflow.adapter.out.persistence.entity.WorkflowInstanceEntity
import com.ntt.sysadminservice.workflow.adapter.out.persistence.entity.WorkflowStepEntity
import jakarta.persistence.EntityManager
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * Workflow service — CRUD for workflow definitions, instance management,
 * delegation support, and guard condition checking (FR-013).
 */
@Service
class WorkflowService(
    private val entityManager: EntityManager,
    private val workflowEngine: WorkflowEngine
) {

    private val log = LoggerFactory.getLogger(WorkflowService::class.java)

    /**
     * Submit an entity for approval via a workflow definition.
     */
    @Transactional
    fun submitForApproval(
        workflowCode: String,
        domainId: Long,
        entityType: String,
        entityId: String,
        submittedBy: Long
    ): WorkflowInstanceEntity {
        val definition = entityManager
            .createQuery("SELECT w FROM WorkflowDefinitionEntity w WHERE w.code = :code AND w.domainId = :domainId AND w.status = 'ACTIVE' AND w.active = true ORDER BY w.version DESC", WorkflowDefinitionEntity::class.java)
            .setParameter("code", workflowCode)
            .setParameter("domainId", domainId)
            .setMaxResults(1)
            .resultList
            .firstOrNull()
            ?: throw SysAdminException(SysAdminErrorCode.WORKFLOW_NOT_FOUND, "Workflow definition not found: $workflowCode")

        val instance = WorkflowInstanceEntity().apply {
            this.workflowDefId = definition.id!!
            this.entityType = entityType
            this.entityId = entityId
            this.submittedBy = submittedBy
            this.status = "PENDING"
            this.submittedAt = Instant.now()
        }
        entityManager.persist(instance)

        // Create steps from definition JSON
        val stepConfigs = parseStepsFromJson(definition.stepsJson)
        stepConfigs.forEachIndexed { index, config ->
            val step = WorkflowStepEntity().apply {
                this.instanceId = instance.id!!
                this.stepOrder = index
                this.approverUserId = config.approverUserId
                this.approverRole = config.approverRole
                this.escalationTimeout = config.escalationTimeout ?: 86400
                this.conditionJson = config.conditionJson
            }
            entityManager.persist(step)
        }

        // Set first step
        instance.currentStep = 0
        instance.status = "IN_PROGRESS"
        entityManager.merge(instance)

        log.info("Workflow submitted: instanceId={}, workflow={}, entity={}/{}", instance.id, workflowCode, entityType, entityId)
        return instance
    }

    /**
     * Get workflow instance details with steps.
     */
    fun getWorkflowInstance(instanceId: Long): WorkflowInstanceEntity {
        return entityManager.find(WorkflowInstanceEntity::class.java, instanceId)
            ?: throw SysAdminException(SysAdminErrorCode.WORKFLOW_NOT_FOUND, "Workflow instance not found: $instanceId")
    }

    /**
     * Get all steps for a workflow instance.
     */
    fun getWorkflowSteps(instanceId: Long): List<WorkflowStepEntity> {
        return entityManager
            .createQuery("SELECT s FROM WorkflowStepEntity s WHERE s.instanceId = :instanceId ORDER BY s.stepOrder", WorkflowStepEntity::class.java)
            .setParameter("instanceId", instanceId)
            .resultList
    }

    /**
     * Process approval/rejection decision.
     */
    @Transactional
    fun processDecision(instanceId: Long, stepId: Long, decision: String, userId: Long, comments: String?): WorkflowInstanceEntity {
        return workflowEngine.processDecision(instanceId, stepId, decision, userId, comments)
    }

    /**
     * Delegate a workflow step to another user (FR-013).
     */
    @Transactional
    fun delegateStep(instanceId: Long, stepId: Long, delegateTo: Long, reason: String?): WorkflowStepEntity {
        return workflowEngine.delegateStep(instanceId, stepId, delegateTo, reason)
    }

    /**
     * Resubmit a rejected workflow (FR-013 — guard: canResubmit).
     */
    @Transactional
    fun resubmitWorkflow(instanceId: Long, userId: Long): WorkflowInstanceEntity {
        return workflowEngine.resubmitWorkflow(instanceId, userId)
    }

    /**
     * Cancel a workflow instance.
     */
    @Transactional
    fun cancelWorkflow(instanceId: Long, userId: Long) {
        val instance = entityManager.find(WorkflowInstanceEntity::class.java, instanceId)
            ?: throw SysAdminException(SysAdminErrorCode.WORKFLOW_NOT_FOUND, "Workflow instance not found: $instanceId")

        val canCancel = workflowEngine.checkGuard("canCancel", instance)
        if (!canCancel) {
            throw SysAdminException(SysAdminErrorCode.ALREADY_PROCESSED, "Workflow already completed")
        }

        instance.status = "CANCELLED"
        instance.completedAt = Instant.now()
        entityManager.merge(instance)
        log.info("Workflow cancelled: instanceId={}, by userId={}", instanceId, userId)
    }

    /**
     * Check available actions for a workflow instance (guard conditions).
     */
    fun getAvailableActions(instanceId: Long): Map<String, Boolean> {
        val instance = entityManager.find(WorkflowInstanceEntity::class.java, instanceId)
            ?: throw SysAdminException(SysAdminErrorCode.WORKFLOW_NOT_FOUND, "Workflow instance not found: $instanceId")

        return mapOf(
            "canCancel" to workflowEngine.checkGuard("canCancel", instance),
            "canResubmit" to workflowEngine.checkGuard("canResubmit", instance),
            "isEscalated" to workflowEngine.checkGuard("isEscalated", instance),
            "isTerminal" to (instance.status in WorkflowEngine.TERMINAL_STATES)
        )
    }

    private data class StepConfig(
        val approverUserId: Long? = null,
        val approverRole: String? = null,
        val escalationTimeout: Int? = null,
        val conditionJson: String? = null
    )

    private fun parseStepsFromJson(json: String): List<StepConfig> {
        if (json == "[]" || json.isBlank()) {
            return listOf(StepConfig(approverRole = "ADMIN", escalationTimeout = 86400))
        }
        return try {
            val mapper = com.fasterxml.jackson.databind.ObjectMapper()
            val arrayNode = mapper.readTree(json)
            if (arrayNode.isArray) {
                arrayNode.map { node ->
                    StepConfig(
                        approverUserId = node.get("approverUserId")?.asLong(),
                        approverRole = node.get("approverRole")?.asText(),
                        escalationTimeout = node.get("escalationTimeout")?.asInt(),
                        conditionJson = node.get("condition")?.toString()
                    )
                }
            } else {
                listOf(StepConfig(approverRole = "ADMIN", escalationTimeout = 86400))
            }
        } catch (e: Exception) {
            log.warn("Failed to parse workflow steps JSON: {}", e.message)
            listOf(StepConfig(approverRole = "ADMIN", escalationTimeout = 86400))
        }
    }
}
