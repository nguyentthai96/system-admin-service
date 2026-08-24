package com.ntt.sysadminservice.shared.exception

import com.ntt.basecore.exception.BusinessException
import org.springframework.http.HttpStatus

/**
 * Base exception for all system admin service errors.
 */
open class SysAdminException(
    val sysAdminError: SysAdminErrorCode,
    override val message: String = sysAdminError.toErrorCodeBase().getDesc() ?: "",
    val httpStatus: HttpStatus = sysAdminError.httpStatus
) : BusinessException(sysAdminError.toErrorCodeBase())

class CircularReferenceException(
    detail: String = "Circular reference detected"
) : SysAdminException(SysAdminErrorCode.CIRCULAR_REFERENCE, detail, HttpStatus.BAD_REQUEST)

class MaxDepthExceededException(
    maxDepth: Int
) : SysAdminException(SysAdminErrorCode.MAX_DEPTH_EXCEEDED, "Maximum tree depth ($maxDepth) exceeded", HttpStatus.BAD_REQUEST)

class WorkflowNotFoundException(
    workflowId: Long
) : SysAdminException(SysAdminErrorCode.WORKFLOW_NOT_FOUND, "Workflow not found: $workflowId", HttpStatus.NOT_FOUND)

class InvalidTransitionException(
    detail: String
) : SysAdminException(SysAdminErrorCode.INVALID_TRANSITION, detail, HttpStatus.BAD_REQUEST)

class AlreadyProcessedException(
    stepId: Long
) : SysAdminException(SysAdminErrorCode.ALREADY_PROCESSED, "Workflow step already processed: $stepId", HttpStatus.CONFLICT)
