package com.ntt.sysadmin.tenant.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.ntt.sysadmin.tenant.adapter.`in`.web.dto.DomainConfigResponse
import com.ntt.sysadmin.tenant.adapter.`in`.web.dto.UpdateDomainConfigRequest
import com.ntt.sysadmin.tenant.adapter.out.persistence.entity.DomainConfigEntity
import com.ntt.sysadmin.tenant.adapter.out.persistence.entity.DomainConfigHistoryEntity
import com.ntt.sysadmin.tenant.adapter.out.persistence.repository.DomainConfigHistoryRepository
import com.ntt.sysadmin.tenant.adapter.out.persistence.repository.DomainConfigRepository
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

/**
 * Domain/Tenant Configuration Service (FR-016).
 *
 * Manages per-domain configuration with:
 * - Redis caching (TTL 30 min)
 * - Config versioning and history tracking
 * - Rollback to previous versions
 * - JSON validation before save
 * - Data isolation by domain (multi-tenant)
 */
@Service
class DomainConfigService(
    private val domainConfigRepository: DomainConfigRepository,
    private val historyRepository: DomainConfigHistoryRepository,
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper
) {

    private val log = LoggerFactory.getLogger(DomainConfigService::class.java)

    companion object {
        private const val CACHE_KEY_PREFIX = "domain:"
        private const val CACHE_KEY_SUFFIX = ":config"
        private val CACHE_TTL: Duration = Duration.ofMinutes(30)
    }

    /**
     * Get domain config (with Redis caching).
     */
    fun getDomainConfig(domainId: Long): DomainConfigResponse {
        // Check Redis cache first
        val cached = getCachedConfig(domainId)
        if (cached != null) {
            return objectMapper.readValue(cached, DomainConfigResponse::class.java)
        }

        val entity = domainConfigRepository.findByDomainId(domainId)
            ?: return createDefaultConfig(domainId)

        val response = entity.toResponse()

        // Cache in Redis
        cacheConfig(domainId, objectMapper.writeValueAsString(response))

        return response
    }

    /**
     * Update domain config with versioning.
     * Saves current state as history snapshot before applying changes.
     */
    @Transactional
    fun updateDomainConfig(
        domainId: Long,
        request: UpdateDomainConfigRequest,
        changedBy: String? = null,
        changeReason: String? = null
    ): DomainConfigResponse {
        val entity = domainConfigRepository.findByDomainId(domainId)
            ?: DomainConfigEntity().apply { this.domainId = domainId }

        // Validate JSON fields before saving
        request.brandingJson?.let { validateJson(it, "brandingJson") }
        request.loginPageConfigJson?.let { validateJson(it, "loginPageConfigJson") }
        request.passwordPolicyJson?.let { validateJson(it, "passwordPolicyJson") }
        request.mfaPolicyJson?.let { validateJson(it, "mfaPolicyJson") }
        request.sessionPolicyJson?.let { validateJson(it, "sessionPolicyJson") }
        request.allowedIpRangesJson?.let { validateJson(it, "allowedIpRangesJson") }

        // Save history snapshot BEFORE updating
        if (entity.id != null) {
            saveHistorySnapshot(entity, changedBy, changeReason)
        }

        // Apply updates
        request.brandingJson?.let { entity.brandingJson = it }
        request.loginPageConfigJson?.let { entity.loginPageConfigJson = it }
        request.passwordPolicyJson?.let { entity.passwordPolicyJson = it }
        request.mfaPolicyJson?.let { entity.mfaPolicyJson = it }
        request.sessionPolicyJson?.let { entity.sessionPolicyJson = it }
        request.allowedIpRangesJson?.let { entity.allowedIpRangesJson = it }
        request.maxUsers?.let { entity.maxUsers = it }
        request.maxApiPartners?.let { entity.maxApiPartners = it }

        val saved = domainConfigRepository.save(entity)

        // Invalidate cache
        invalidateCache(domainId)

        log.info("Domain config updated for domain {} by {}", domainId, changedBy)
        return saved.toResponse()
    }

    /**
     * Get branding config for a domain (public endpoint).
     */
    fun getBranding(domainId: Long): Any? {
        val config = getDomainConfig(domainId)
        return config.branding
    }

    // ===============================
    // Config History & Versioning (FR-016)
    // ===============================

    /**
     * Get config change history for a domain.
     */
    fun getConfigHistory(domainId: Long): List<Map<String, Any?>> {
        return historyRepository.findByDomainIdOrderByVersionDesc(domainId).map { h ->
            mapOf(
                "id" to h.id,
                "version" to h.version,
                "changedBy" to h.changedBy,
                "changedAt" to h.changedAt.toString(),
                "changeReason" to h.changeReason
            )
        }
    }

    /**
     * Rollback domain config to a specific history version.
     */
    @Transactional
    fun rollbackConfig(domainId: Long, historyId: Long, rolledBackBy: String? = null): DomainConfigResponse {
        val history = historyRepository.findById(historyId)
            .orElseThrow { IllegalArgumentException("Config history not found: $historyId") }

        if (history.domainId != domainId) {
            throw IllegalArgumentException("History entry does not belong to domain $domainId")
        }

        val entity = domainConfigRepository.findByDomainId(domainId)
            ?: throw IllegalArgumentException("Domain config not found for domain $domainId")

        // Save current state as history before rollback
        saveHistorySnapshot(entity, rolledBackBy, "Rollback to version ${history.version}")

        // Restore from snapshot
        val snapshot = objectMapper.readValue(history.configSnapshot, Map::class.java)
        entity.brandingJson = snapshot["brandingJson"] as? String
        entity.loginPageConfigJson = snapshot["loginPageConfigJson"] as? String
        entity.passwordPolicyJson = snapshot["passwordPolicyJson"] as? String
        entity.mfaPolicyJson = snapshot["mfaPolicyJson"] as? String
        entity.sessionPolicyJson = snapshot["sessionPolicyJson"] as? String
        entity.allowedIpRangesJson = snapshot["allowedIpRangesJson"] as? String
        entity.maxUsers = (snapshot["maxUsers"] as? Number)?.toInt() ?: entity.maxUsers
        entity.maxApiPartners = (snapshot["maxApiPartners"] as? Number)?.toInt() ?: entity.maxApiPartners

        val saved = domainConfigRepository.save(entity)
        invalidateCache(domainId)

        log.info("Domain config rolled back to version {} for domain {} by {}", history.version, domainId, rolledBackBy)
        return saved.toResponse()
    }

    // ===============================
    // Private Helpers
    // ===============================

    private fun createDefaultConfig(domainId: Long): DomainConfigResponse {
        return DomainConfigResponse(
            domainId = domainId,
            branding = null,
            loginPageConfig = null,
            passwordPolicy = null,
            mfaPolicy = null,
            sessionPolicy = null,
            allowedIpRanges = null,
            maxUsers = 1000,
            maxApiPartners = 50
        )
    }

    /**
     * Save current config state as a history snapshot.
     */
    private fun saveHistorySnapshot(entity: DomainConfigEntity, changedBy: String?, changeReason: String?) {
        val latestVersion = historyRepository.findTopByDomainIdOrderByVersionDesc(entity.domainId)?.version ?: 0

        val snapshot = objectMapper.writeValueAsString(mapOf(
            "brandingJson" to entity.brandingJson,
            "loginPageConfigJson" to entity.loginPageConfigJson,
            "passwordPolicyJson" to entity.passwordPolicyJson,
            "mfaPolicyJson" to entity.mfaPolicyJson,
            "sessionPolicyJson" to entity.sessionPolicyJson,
            "allowedIpRangesJson" to entity.allowedIpRangesJson,
            "maxUsers" to entity.maxUsers,
            "maxApiPartners" to entity.maxApiPartners
        ))

        val history = DomainConfigHistoryEntity().apply {
            this.domainId = entity.domainId
            this.configSnapshot = snapshot
            this.changedBy = changedBy
            this.changedAt = Instant.now()
            this.changeReason = changeReason
            this.version = latestVersion + 1
        }
        historyRepository.save(history)
    }

    /**
     * Validate that a string is valid JSON.
     */
    private fun validateJson(json: String, fieldName: String) {
        try {
            objectMapper.readTree(json)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid JSON in field $fieldName: ${e.message}")
        }
    }

    // ===============================
    // Redis Cache
    // ===============================

    private fun getCachedConfig(domainId: Long): String? {
        val key = "$CACHE_KEY_PREFIX$domainId$CACHE_KEY_SUFFIX"
        return redisTemplate.opsForValue().get(key)
    }

    private fun cacheConfig(domainId: Long, json: String) {
        val key = "$CACHE_KEY_PREFIX$domainId$CACHE_KEY_SUFFIX"
        redisTemplate.opsForValue().set(key, json, CACHE_TTL)
    }

    private fun invalidateCache(domainId: Long) {
        val key = "$CACHE_KEY_PREFIX$domainId$CACHE_KEY_SUFFIX"
        redisTemplate.delete(key)
    }

    private fun DomainConfigEntity.toResponse(): DomainConfigResponse {
        return DomainConfigResponse(
            domainId = this.domainId,
            branding = this.brandingJson?.let { tryParseJson(it) },
            loginPageConfig = this.loginPageConfigJson?.let { tryParseJson(it) },
            passwordPolicy = this.passwordPolicyJson?.let { tryParseJson(it) },
            mfaPolicy = this.mfaPolicyJson?.let { tryParseJson(it) },
            sessionPolicy = this.sessionPolicyJson?.let { tryParseJson(it) },
            allowedIpRanges = this.allowedIpRangesJson?.let { tryParseJson(it) },
            maxUsers = this.maxUsers,
            maxApiPartners = this.maxApiPartners
        )
    }

    private fun tryParseJson(json: String): Any {
        return try {
            objectMapper.readValue(json, Any::class.java)
        } catch (e: Exception) {
            json // Return raw string if not valid JSON
        }
    }
}

