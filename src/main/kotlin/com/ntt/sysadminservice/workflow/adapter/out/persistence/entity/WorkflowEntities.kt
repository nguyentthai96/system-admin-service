package com.ntt.sysadminservice.workflow.adapter.out.persistence.entity

import com.ntt.basecore.model.id.SnowflakePersistentAuditableEntity
import jakarta.persistence.*
import java.time.Instant

/**
 * Workflow definition — multi-step approval workflow template (FR-013).
 * Steps defined as JSON array. Version immutable.
 */
@Entity
@Table(
    name = "workflow_definitions",
    uniqueConstraints = [UniqueConstraint(columnNames = ["domain_id", "code", "version"])]
)
class WorkflowDefinitionEntity : SnowflakePersistentAuditableEntity() {

    @Column(name = "domain_id", nullable = false)
    var domainId: Long = 0

    @Column(nullable = false, length = 50)
    lateinit var code: String

    @Column(nullable = false, length = 200)
    lateinit var name: String

    @Column(length = 500)
    var description: String? = null

    /** JSON array defining step configurations. */
    @Column(name = "steps_json", nullable = false, columnDefinition = "jsonb")
    var stepsJson: String = "[]"

    @Column(nullable = false)
    var version: Int = 1

    @Column(nullable = false, length = 20)
    var status: String = "ACTIVE"
}

/**
 * Workflow instance — represents an active approval process (FR-013).
 * State machine: PENDING → IN_PROGRESS → APPROVED/REJECTED/ESCALATED/CANCELLED
 */
@Entity
@Table(name = "workflow_instances")
class WorkflowInstanceEntity : SnowflakePersistentAuditableEntity() {

    @Column(name = "workflow_def_id", nullable = false)
    var workflowDefId: Long = 0

    @Column(name = "entity_type", nullable = false, length = 100)
    lateinit var entityType: String

    @Column(name = "entity_id", nullable = false, length = 100)
    lateinit var entityId: String

    @Column(name = "submitted_by", nullable = false)
    var submittedBy: Long = 0

    @Column(name = "current_step", nullable = false)
    var currentStep: Int = 0

    /** Status: PENDING, IN_PROGRESS, APPROVED, REJECTED, ESCALATED, CANCELLED */
    @Column(nullable = false, length = 30)
    var status: String = "PENDING"

    @Column(name = "submitted_at", nullable = false)
    var submittedAt: Instant = Instant.now()

    @Column(name = "completed_at")
    var completedAt: Instant? = null
}

/**
 * Workflow step — individual approval step within an instance (FR-013).
 * Enhanced with delegation support and conditional routing.
 */
@Entity
@Table(name = "workflow_steps")
class WorkflowStepEntity : SnowflakePersistentAuditableEntity() {

    @Column(name = "instance_id", nullable = false)
    var instanceId: Long = 0

    @Column(name = "step_order", nullable = false)
    var stepOrder: Int = 0

    @Column(name = "approver_user_id")
    var approverUserId: Long? = null

    @Column(name = "approver_role", length = 100)
    var approverRole: String? = null

    /** Status: PENDING, APPROVED, REJECTED, ESCALATED, SKIPPED */
    @Column(nullable = false, length = 30)
    var status: String = "PENDING"

    @Column(length = 30)
    var decision: String? = null

    @Column(name = "decided_by")
    var decidedBy: Long? = null

    @Column(length = 2000)
    var comments: String? = null

    @Column(name = "decided_at")
    var decidedAt: Instant? = null

    /** Timeout in seconds before auto-escalation (default 24h). */
    @Column(name = "escalation_timeout")
    var escalationTimeout: Int = 86400

    @Column(nullable = false)
    var escalated: Boolean = false

    // ─── Delegation Support (FR-013) ────────────────────────────

    /** Original approver before delegation. */
    @Column(name = "delegated_from")
    var delegatedFrom: Long? = null

    /** Reason for delegation. */
    @Column(name = "delegation_reason", length = 500)
    var delegationReason: String? = null

    // ─── Conditional Routing (FR-013) ───────────────────────────

    /** JSONB condition for conditional routing (PBAC PolicyCondition DSL). */
    @Column(name = "condition_json", columnDefinition = "jsonb")
    var conditionJson: String? = null
}
