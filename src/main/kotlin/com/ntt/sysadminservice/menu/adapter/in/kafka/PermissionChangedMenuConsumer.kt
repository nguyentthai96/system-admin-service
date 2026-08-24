package com.ntt.sysadminservice.menu.adapter.`in`.kafka

import com.ntt.sysadminservice.menu.application.MenuPermissionService
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

/**
 * Kafka consumer for menu cache invalidation on permission changes (FR-010).
 * Listens to: iam.permission.changed
 * Action: Invalidate Redis menu cache for affected users.
 */
@Component
class PermissionChangedMenuConsumer(
    private val menuPermissionService: MenuPermissionService
) {

    private val log = LoggerFactory.getLogger(PermissionChangedMenuConsumer::class.java)

    /**
     * Handle permission changed event — invalidate affected user's menu cache.
     * If roleIds changed, invalidate all caches (role affects multiple users).
     * If specific userId, invalidate only that user's cache.
     */
    @KafkaListener(topics = ["iam.permission.changed"], groupId = "sysadmin-menu-cache-group")
    fun onPermissionChanged(event: PermissionChangedEvent) {
        log.info("Received permission.changed event: userId={}, action={}", event.userId, event.action)
        try {
            if (event.userId != null) {
                menuPermissionService.invalidateUserMenuCache(event.userId)
            } else {
                // Role-level change affects all users — invalidate all caches
                menuPermissionService.invalidateAllMenuCaches()
            }
        } catch (e: Exception) {
            log.error("Failed to process permission.changed event: {}", e.message, e)
        }
    }

    /**
     * Event DTO for permission change (matches auth-service PermissionChangedEvent).
     */
    data class PermissionChangedEvent(
        val userId: Long? = null,
        val roleIds: List<Long> = emptyList(),
        val action: String = "UNKNOWN"
    )
}
