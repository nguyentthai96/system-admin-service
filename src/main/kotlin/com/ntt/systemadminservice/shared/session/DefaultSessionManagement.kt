package com.ntt.systemadminservice.shared.session

import com.ntt.basecore.domain.session.SessionManagement
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import java.util.Optional

/**
 * Default SessionManagement implementation for system-admin-service.
 * Retrieves the current user from Spring Security context.
 * Falls back to "SYSTEM" for batch/admin jobs without user-level authentication.
 */
@Component
class DefaultSessionManagement : SessionManagement {

    override fun getSessionUserCurrent(): Optional<String> {
        val authentication = SecurityContextHolder.getContext().authentication
        return if (authentication != null && authentication.isAuthenticated
            && authentication.name != "anonymousUser") {
            Optional.of(authentication.name)
        } else {
            Optional.of("SYSTEM")
        }
    }
}

