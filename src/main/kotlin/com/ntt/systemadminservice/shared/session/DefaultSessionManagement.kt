package com.ntt.systemadminservice.shared.session

import com.ntt.basecore.domain.session.SessionManagement
import org.springframework.stereotype.Component
import java.util.Optional

/**
 * Default SessionManagement implementation for system-admin-service.
 * Returns "SYSTEM" as the current user since this service
 * runs batch/admin jobs without user-level authentication.
 */
@Component
class DefaultSessionManagement : SessionManagement {

    override fun getSessionUserCurrent(): Optional<String> {
        return Optional.of("SYSTEM")
    }
}
