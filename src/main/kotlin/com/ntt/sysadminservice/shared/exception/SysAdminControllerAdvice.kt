package com.ntt.sysadminservice.shared.exception

import com.ntt.basecore.domain.web.BaseControllerAdvice
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import jakarta.servlet.http.HttpServletResponse
import java.net.URI

/**
 * System admin controller advice — extends BaseControllerAdvice (base-core).
 */
@RestControllerAdvice
class SysAdminControllerAdvice(
    validator: LocalValidatorFactoryBean,
    private val messageSource: MessageSource
) : BaseControllerAdvice(validator) {

    @ExceptionHandler(SysAdminException::class)
    fun handleSysAdminException(
        ex: SysAdminException,
        response: HttpServletResponse
    ): ResponseEntity<ProblemDetail> {
        val detail = resolveMessage(
            ex.sysAdminError.toErrorCodeBase().getMsgCode(),
            null,
            ex.message ?: "System admin error"
        )

        val problem = ProblemDetail.forStatusAndDetail(ex.httpStatus, detail)
        problem.title = ex.sysAdminError.getErrorCode()
        problem.type = URI.create("https://system-admin-service/errors/${ex.sysAdminError.name.lowercase()}")
        problem.setProperty("errorCode", ex.sysAdminError.getErrorCode())

        response.setHeader("Content-Language", LocaleContextHolder.getLocale().toLanguageTag())

        return ResponseEntity.status(ex.httpStatus).body(problem)
    }

    private fun resolveMessage(msgCode: String?, args: Array<Any>?, defaultMessage: String): String {
        if (msgCode.isNullOrBlank()) return defaultMessage
        return messageSource.getMessage(msgCode, args, defaultMessage, LocaleContextHolder.getLocale())
            ?: defaultMessage
    }
}
