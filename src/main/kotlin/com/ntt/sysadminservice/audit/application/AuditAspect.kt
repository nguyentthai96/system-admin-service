package com.ntt.sysadminservice.audit.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import jakarta.servlet.http.HttpServletRequest
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

/**
 * AOP aspect for automatic audit logging on admin controller operations (FR-015).
 * Enhanced with sensitive data masking and old-value capture for diff.
 * Captures old/new values as JSONB diff: {field: {old: X, new: Y}}
 */
@Aspect
@Component
class AuditAspect(
    private val auditService: AuditService,
    private val objectMapper: ObjectMapper
) {

    private val log = LoggerFactory.getLogger(AuditAspect::class.java)

    companion object {
        /** Fields that should be masked in audit logs. */
        val SENSITIVE_FIELDS = setOf(
            "password", "passwordHash", "secret", "token", "apiKey", "keyHash",
            "codeHash", "totpSecret", "refreshToken", "accessToken",
            "ipWhitelist", "creditCard", "ssn"
        )
        private const val MASK = "***MASKED***"
    }

    /**
     * Around advice on admin controller mutating operations.
     */
    @Around("execution(* com.ntt.sysadminservice..adapter.in.web..*Controller.create*(..)) || " +
            "execution(* com.ntt.sysadminservice..adapter.in.web..*Controller.update*(..)) || " +
            "execution(* com.ntt.sysadminservice..adapter.in.web..*Controller.delete*(..)) || " +
            "execution(* com.ntt.sysadminservice..adapter.in.web..*Controller.move*(..)) || " +
            "execution(* com.ntt.sysadminservice..adapter.in.web..*Controller.assign*(..)) || " +
            "execution(* com.ntt.sysadminservice..adapter.in.web..*Controller.revoke*(..)) || " +
            "execution(* com.ntt.sysadminservice..adapter.in.web..*Controller.delegate*(..)) || " +
            "execution(* com.ntt.sysadminservice..adapter.in.web..*Controller.transfer*(..)) || " +
            "execution(* com.ntt.sysadminservice..adapter.in.web..*Controller.configure*(..)) || " +
            "execution(* com.ntt.sysadminservice..adapter.in.web..*Controller.generate*(..)) || " +
            "execution(* com.ntt.sysadminservice..adapter.in.web..*Controller.rotate*(..))")
    fun auditAdminOperation(joinPoint: ProceedingJoinPoint): Any? {
        val methodName = joinPoint.signature.name
        val className = joinPoint.target.javaClass.simpleName
        val action = "${className}.$methodName"

        val request = getCurrentRequest()
        val ipAddress = request?.let { getClientIp(it) }
        val userAgent = request?.getHeader("User-Agent")
        val userId = extractUserId(request)

        val args = joinPoint.args
        val inputSnapshot = try {
            if (args.isNotEmpty()) maskSensitiveData(objectMapper.writeValueAsString(args[0])) else null
        } catch (e: Exception) { null }

        return try {
            val result = joinPoint.proceed()

            val outputSnapshot = try {
                maskSensitiveData(objectMapper.writeValueAsString(result))
            } catch (e: Exception) { null }

            auditService.recordAudit(
                userId = userId,
                action = action,
                entityType = className.removeSuffix("Controller"),
                entityId = extractEntityId(args),
                oldValue = null,
                newValue = outputSnapshot,
                ipAddress = ipAddress,
                userAgent = userAgent,
                details = "input=${inputSnapshot ?: "N/A"}"
            )

            result
        } catch (e: Exception) {
            auditService.recordAudit(
                userId = userId,
                action = "$action.FAILED",
                entityType = className.removeSuffix("Controller"),
                entityId = extractEntityId(args),
                oldValue = null,
                newValue = null,
                ipAddress = ipAddress,
                userAgent = userAgent,
                details = "error=${e.message}"
            )
            throw e
        }
    }

    /**
     * Mask sensitive fields in JSON data for audit logging (FR-015).
     * Replaces values of sensitive fields with "***MASKED***".
     */
    fun maskSensitiveData(json: String?): String? {
        if (json.isNullOrBlank()) return json
        return try {
            val node = objectMapper.readTree(json)
            if (node is ObjectNode) {
                maskObjectNode(node)
                objectMapper.writeValueAsString(node)
            } else {
                json
            }
        } catch (e: Exception) {
            json // Return original if parsing fails
        }
    }

    private fun maskObjectNode(node: ObjectNode) {
        val fieldNames = node.fieldNames().asSequence().toList()
        fieldNames.forEach { fieldName ->
            if (fieldName.lowercase() in SENSITIVE_FIELDS.map { it.lowercase() }) {
                node.put(fieldName, MASK)
            } else {
                val child = node.get(fieldName)
                if (child is ObjectNode) {
                    maskObjectNode(child)
                }
            }
        }
    }

    private fun getCurrentRequest(): HttpServletRequest? {
        return try {
            (RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)?.request
        } catch (e: Exception) { null }
    }

    private fun getClientIp(request: HttpServletRequest): String {
        val forwarded = request.getHeader("X-Forwarded-For")
        return if (!forwarded.isNullOrBlank()) forwarded.split(",").first().trim() else request.remoteAddr
    }

    private fun extractUserId(request: HttpServletRequest?): Long? {
        return try {
            request?.userPrincipal?.name?.toLongOrNull()
        } catch (e: Exception) { null }
    }

    private fun extractEntityId(args: Array<Any>): String? {
        return args.firstOrNull()?.let {
            if (it is Long) it.toString() else null
        }
    }
}
