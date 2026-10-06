package com.ntt.sysadminservice.menu.adapter.out.event

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

/**
 * Publishes Kafka events when menu permissions change.
 *
 * Topic: menu.permission.changed
 * Key: menuCode (for partition ordering)
 *
 * Reuses the same fire-and-forget-with-callback pattern as ConfigEventPublisher.
 */
@Component
class MenuEventPublisher(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(MenuEventPublisher::class.java)

    companion object {
        const val TOPIC = "menu.permission.changed"
    }

    /**
     * Publish a menu permission changed event to Kafka.
     *
     * @param action CREATED, UPDATED, or DELETED
     * @param menuCode unique menu identifier (kebab-case)
     * @param menuPath API path associated with the menu (nullable)
     * @param menuType DIRECTORY, MENU, BUTTON, API
     * @param domainId domain ID for multi-tenant
     * @param permissions list of permission definitions (code + name)
     * @param assignedRoles list of role assignments with their permissions
     */
    fun publishMenuPermissionChanged(
        action: String,
        menuCode: String,
        menuPath: String?,
        menuType: String,
        domainId: Long,
        permissions: List<PermissionInfo>,
        assignedRoles: List<RoleAssignment>
    ) {
        val event = mapOf(
            "eventType" to "MENU_PERMISSION_CHANGED",
            "timestamp" to Instant.now().toString(),
            "correlationId" to UUID.randomUUID().toString(),
            "payload" to mapOf(
                "action" to action,
                "menuCode" to menuCode,
                "menuPath" to menuPath,
                "menuType" to menuType,
                "domainId" to domainId,
                "permissions" to permissions.map { mapOf("code" to it.code, "name" to it.name) },
                "assignedRoles" to assignedRoles.map { role ->
                    mapOf(
                        "roleId" to role.roleId,
                        "roleName" to role.roleName,
                        "permissions" to role.permissions
                    )
                }
            )
        )

        val payload = objectMapper.writeValueAsString(event)

        kafkaTemplate.send(TOPIC, menuCode, payload).whenComplete { _, ex ->
            if (ex != null) {
                log.error("Failed to publish menu permission changed event for menuCode={}", menuCode, ex)
            } else {
                log.info("Published menu permission changed event: action={}, menuCode={}", action, menuCode)
            }
        }
    }

    // Simple DTOs for publisher (avoid importing auth-service models)
    data class PermissionInfo(val code: String, val name: String = "")
    data class RoleAssignment(val roleId: Long, val roleName: String = "", val permissions: List<String> = emptyList())
}
