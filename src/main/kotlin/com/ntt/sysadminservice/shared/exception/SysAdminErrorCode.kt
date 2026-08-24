package com.ntt.sysadminservice.shared.exception

import com.ntt.basecore.exception.base.ErrorCodeBase
import org.springframework.http.HttpStatus

/**
 * System admin service error codes — implements base-core ErrorCodeBase.
 * Pattern: Same as AuthErrorCode — enum with errorCode, msgCode, httpStatus.
 */
enum class SysAdminErrorCode(
    private val errorCode: String,
    private val msgCode: String,
    private val description: String,
    val httpStatus: HttpStatus
) {
    GENERAL_ERROR("SYS_001", "sysadmin.general_error", "System admin service error", HttpStatus.INTERNAL_SERVER_ERROR),
    CIRCULAR_REFERENCE("SYS_002", "sysadmin.circular_reference", "Circular reference detected in menu tree", HttpStatus.BAD_REQUEST),
    PERMISSION_DENIED("SYS_003", "sysadmin.permission_denied", "Insufficient permissions", HttpStatus.FORBIDDEN),
    NOT_FOUND("SYS_004", "sysadmin.not_found", "Resource not found", HttpStatus.NOT_FOUND),
    CIRCULAR_HIERARCHY("SYS_005", "sysadmin.circular_hierarchy", "Circular hierarchy detected in department tree", HttpStatus.BAD_REQUEST),
    POSITION_DUPLICATE("SYS_006", "sysadmin.position_duplicate", "Position code already exists in department", HttpStatus.CONFLICT),
    PARTNER_NOT_FOUND("SYS_007", "sysadmin.partner_not_found", "API partner not found", HttpStatus.NOT_FOUND),
    API_KEY_EXPIRED("SYS_008", "sysadmin.api_key_expired", "API key has expired", HttpStatus.UNAUTHORIZED),
    RATE_LIMIT_EXCEEDED("SYS_009", "sysadmin.rate_limit_exceeded", "API rate limit exceeded", HttpStatus.TOO_MANY_REQUESTS),
    IP_NOT_WHITELISTED("SYS_010", "sysadmin.ip_not_whitelisted", "IP address not whitelisted", HttpStatus.FORBIDDEN),
    WORKFLOW_NOT_FOUND("SYS_011", "sysadmin.workflow_not_found", "Workflow not found", HttpStatus.NOT_FOUND),
    INVALID_TRANSITION("SYS_012", "sysadmin.invalid_transition", "Invalid workflow state transition", HttpStatus.BAD_REQUEST),
    ALREADY_PROCESSED("SYS_013", "sysadmin.already_processed", "Workflow step already processed", HttpStatus.CONFLICT),
    ESCALATION_TIMEOUT("SYS_014", "sysadmin.escalation_timeout", "Workflow escalation timeout exceeded", HttpStatus.REQUEST_TIMEOUT),
    CONFIG_NOT_FOUND("SYS_015", "sysadmin.config_not_found", "System config not found", HttpStatus.NOT_FOUND),
    INVALID_CONFIG_TYPE("SYS_016", "sysadmin.invalid_config_type", "Invalid config value type", HttpStatus.BAD_REQUEST),
    AUDIT_QUERY_FAILED("SYS_017", "sysadmin.audit_query_failed", "Audit query failed", HttpStatus.INTERNAL_SERVER_ERROR),
    MAX_DEPTH_EXCEEDED("SYS_018", "sysadmin.max_depth_exceeded", "Maximum tree depth exceeded", HttpStatus.BAD_REQUEST);

    private val delegate = object : ErrorCodeBase(errorCode, msgCode, description) {}

    fun getErrorCode(): String = errorCode
    fun toErrorCodeBase(): ErrorCodeBase = delegate
    override fun toString(): String = errorCode
}
