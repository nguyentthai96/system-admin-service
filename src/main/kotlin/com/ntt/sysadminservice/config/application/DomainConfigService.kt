package com.ntt.sysadminservice.config.application

import com.ntt.sysadminservice.shared.exception.SysAdminErrorCode
import com.ntt.sysadminservice.shared.exception.SysAdminException
import jakarta.persistence.EntityManager
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Domain config service — CRUD with type validation and versioning (FR-014).
 * Supports STRING, JSON, NUMBER config types.
 * 
 * ⚠️ Assumption: DomainConfigEntity and DomainConfigHistoryEntity already exist.
 * This modification adds type validation and history tracking.
 */
@Service
class DomainConfigService(
    private val entityManager: EntityManager
) {

    private val log = LoggerFactory.getLogger(DomainConfigService::class.java)

    companion object {
        val VALID_TYPES = setOf("STRING", "JSON", "NUMBER")
    }

    /**
     * Get a config value with type validation.
     */
    fun getConfig(domainId: Long, key: String): Map<String, Any?> {
        @Suppress("UNCHECKED_CAST")
        val result = entityManager
            .createNativeQuery("SELECT config_key, config_value, value_type, version FROM system_configs WHERE domain_id = :domainId AND config_key = :key AND active = true")
            .setParameter("domainId", domainId)
            .setParameter("key", key)
            .resultList as List<Array<Any?>>

        if (result.isEmpty()) {
            throw SysAdminException(SysAdminErrorCode.CONFIG_NOT_FOUND, "Config not found: $key")
        }

        val row = result.first()
        return mapOf(
            "key" to row[0],
            "value" to row[1],
            "type" to row[2],
            "version" to row[3]
        )
    }

    /**
     * Update config with type validation and history tracking.
     */
    @Transactional
    fun updateConfig(domainId: Long, key: String, value: String, valueType: String): Map<String, Any?> {
        if (valueType !in VALID_TYPES) {
            throw SysAdminException(SysAdminErrorCode.INVALID_CONFIG_TYPE, "Invalid config type: $valueType. Valid: $VALID_TYPES")
        }

        // Validate value against type
        validateConfigValue(value, valueType)

        // Get current value for history
        val oldValue = try { getConfig(domainId, key) } catch (e: Exception) { null }

        if (oldValue != null) {
            // Record history
            entityManager.createNativeQuery("""
                INSERT INTO domain_config_history (id, domain_id, config_key, old_value, new_value, changed_at, changed_by, active, created_at)
                VALUES (nextval('snowflake_seq'), :domainId, :key, :oldVal, :newVal, NOW(), 'SYSTEM', true, NOW())
            """)
                .setParameter("domainId", domainId)
                .setParameter("key", key)
                .setParameter("oldVal", oldValue["value"]?.toString())
                .setParameter("newVal", value)
                .executeUpdate()

            // Update config with version increment
            entityManager.createNativeQuery("""
                UPDATE system_configs 
                SET config_value = :value, value_type = :type, version = version + 1, updated_at = NOW()
                WHERE domain_id = :domainId AND config_key = :key AND active = true
            """)
                .setParameter("value", value)
                .setParameter("type", valueType)
                .setParameter("domainId", domainId)
                .setParameter("key", key)
                .executeUpdate()
        } else {
            // Insert new config
            entityManager.createNativeQuery("""
                INSERT INTO system_configs (id, domain_id, config_key, config_value, value_type, version, active, created_at, updated_at)
                VALUES (nextval('snowflake_seq'), :domainId, :key, :value, :type, 1, true, NOW(), NOW())
            """)
                .setParameter("domainId", domainId)
                .setParameter("key", key)
                .setParameter("value", value)
                .setParameter("type", valueType)
                .executeUpdate()
        }

        log.info("Config updated: domain={}, key={}, type={}", domainId, key, valueType)
        return getConfig(domainId, key)
    }

    private fun validateConfigValue(value: String, type: String) {
        when (type) {
            "NUMBER" -> {
                if (value.toDoubleOrNull() == null) {
                    throw SysAdminException(SysAdminErrorCode.INVALID_CONFIG_TYPE, "Value '$value' is not a valid number")
                }
            }
            "JSON" -> {
                try {
                    com.fasterxml.jackson.databind.ObjectMapper().readTree(value)
                } catch (e: Exception) {
                    throw SysAdminException(SysAdminErrorCode.INVALID_CONFIG_TYPE, "Value is not valid JSON: ${e.message}")
                }
            }
            "STRING" -> { /* All strings are valid */ }
        }
    }
}
