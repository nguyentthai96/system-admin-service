package com.ntt.sysadmin.apipartner

import com.ntt.sysadmin.apipartner.adapter.out.persistence.entity.ApiKeyEntity
import com.ntt.sysadmin.apipartner.adapter.out.persistence.repository.ApiKeyRepository
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.Duration
import java.util.UUID

/**
 * API Key lifecycle management (FR-012).
 *
 * BR-API-01: API key shown only once at generation, stored as SHA-256 hash
 * BR-API-04: Key prefix format: ntt_pk_ (production) / ntt_sk_ (sandbox)
 */
@Service
class ApiKeyService(
    private val apiKeyRepository: ApiKeyRepository,
    private val redisTemplate: StringRedisTemplate
) {

    private val log = LoggerFactory.getLogger(ApiKeyService::class.java)

    /**
     * Generate a new API key for a partner.
     * Returns the raw key (shown ONLY ONCE — BR-API-01).
     */
    @Transactional
    fun generateApiKey(
        partnerId: Long,
        name: String?,
        environment: String = "PRODUCTION",
        rateLimitPerSecond: Int = 10,
        rateLimitPerMinute: Int = 600,
        rateLimitPerDay: Long = 10000,
        quotaMonthly: Long = 300000,
        scopes: List<String> = emptyList(),
        ipWhitelist: List<String> = emptyList(),
        expiresInDays: Long? = null
    ): Pair<ApiKeyEntity, String> {
        // Generate raw key with prefix (BR-API-04)
        val prefix = if (environment == "SANDBOX") ApiKeyEntity.PREFIX_SANDBOX else ApiKeyEntity.PREFIX_PRODUCTION
        val rawKey = "$prefix${UUID.randomUUID().toString().replace("-", "")}"

        // Hash for storage (BR-API-01)
        val keyHash = hashApiKey(rawKey)

        val expiresAt = expiresInDays?.let {
            System.currentTimeMillis() + Duration.ofDays(it).toMillis()
        }

        val entity = ApiKeyEntity().apply {
            this.partnerId = partnerId
            this.keyPrefix = prefix
            this.keyHash = keyHash
            this.name = name
            this.scopesJson = if (scopes.isNotEmpty()) "[\"${scopes.joinToString("\",\"")}\"]" else null
            this.rateLimitPerSecond = rateLimitPerSecond
            this.rateLimitPerMinute = rateLimitPerMinute
            this.rateLimitPerDay = rateLimitPerDay
            this.quotaMonthly = quotaMonthly
            this.ipWhitelistJson = if (ipWhitelist.isNotEmpty()) "[\"${ipWhitelist.joinToString("\",\"")}\"]" else null
            this.expiresAt = expiresAt
            this.status = ApiKeyEntity.STATUS_ACTIVE
        }

        val saved = apiKeyRepository.save(entity)

        // Sync rate limit config to Redis for Gateway
        syncRateLimitToRedis(keyHash, rateLimitPerSecond, rateLimitPerMinute)

        log.info("API key generated for partner {}. Prefix: {}", partnerId, prefix)

        // Return entity + raw key (displayed only once)
        return Pair(saved, rawKey)
    }

    /**
     * Rotate an API key — generate new, revoke old.
     */
    @Transactional
    fun rotateApiKey(oldKeyId: Long): Pair<ApiKeyEntity, String> {
        val oldKey = apiKeyRepository.findById(oldKeyId)
            .orElseThrow { IllegalArgumentException("API key not found: $oldKeyId") }

        // Revoke old key
        oldKey.status = ApiKeyEntity.STATUS_REVOKED
        apiKeyRepository.save(oldKey)
        removeRateLimitFromRedis(oldKey.keyHash)

        // Generate new key with same config
        return generateApiKey(
            partnerId = oldKey.partnerId,
            name = oldKey.name,
            environment = if (oldKey.keyPrefix == ApiKeyEntity.PREFIX_SANDBOX) "SANDBOX" else "PRODUCTION",
            rateLimitPerSecond = oldKey.rateLimitPerSecond,
            rateLimitPerMinute = oldKey.rateLimitPerMinute,
            rateLimitPerDay = oldKey.rateLimitPerDay,
            quotaMonthly = oldKey.quotaMonthly
        )
    }

    /**
     * Revoke an API key.
     */
    @Transactional
    fun revokeApiKey(keyId: Long) {
        val key = apiKeyRepository.findById(keyId)
            .orElseThrow { IllegalArgumentException("API key not found: $keyId") }
        key.status = ApiKeyEntity.STATUS_REVOKED
        apiKeyRepository.save(key)
        removeRateLimitFromRedis(key.keyHash)

        // Publish revocation event via Redis for instant gateway invalidation
        redisTemplate.convertAndSend("channel:api_key_revoked", key.keyHash)
        log.info("API key {} revoked", keyId)
    }

    /**
     * Suspend an API key.
     */
    @Transactional
    fun suspendApiKey(keyId: Long) {
        val key = apiKeyRepository.findById(keyId)
            .orElseThrow { IllegalArgumentException("API key not found: $keyId") }
        key.status = ApiKeyEntity.STATUS_SUSPENDED
        apiKeyRepository.save(key)
        removeRateLimitFromRedis(key.keyHash)
        log.info("API key {} suspended", keyId)
    }

    /**
     * Validate an API key against stored hash.
     */
    fun validateApiKey(rawKey: String): ApiKeyEntity? {
        val hash = hashApiKey(rawKey)
        val key = apiKeyRepository.findByKeyHash(hash) ?: return null

        if (key.status != ApiKeyEntity.STATUS_ACTIVE) return null
        if (key.expiresAt != null && key.expiresAt!! < System.currentTimeMillis()) return null

        // Update last used
        key.lastUsedAt = System.currentTimeMillis()
        apiKeyRepository.save(key)
        return key
    }

    /**
     * Get all API keys for a partner (metadata only — no raw keys).
     */
    fun getPartnerApiKeys(partnerId: Long): List<ApiKeyEntity> {
        return apiKeyRepository.findByPartnerId(partnerId)
    }

    // ===============================
    // Redis Rate Limit Sync (BR-API-02)
    // ===============================

    /**
     * Sync rate limit config to Redis for API Gateway consumption.
     * Key pattern: rate_limit:bucket4j:{keyHash}
     */
    fun syncRateLimitToRedis(keyHash: String, limitPerSecond: Int, limitPerMinute: Int) {
        val redisKey = "rate_limit:bucket4j:$keyHash"
        redisTemplate.opsForHash<String, String>().putAll(redisKey, mapOf(
            "limitPerSecond" to limitPerSecond.toString(),
            "limitPerMinute" to limitPerMinute.toString()
        ))
        log.debug("Synced rate limit to Redis for key hash: {}", keyHash.take(8))
    }

    private fun removeRateLimitFromRedis(keyHash: String) {
        val redisKey = "rate_limit:bucket4j:$keyHash"
        redisTemplate.delete(redisKey)
    }

    /**
     * Hash API key using SHA-256.
     */
    private fun hashApiKey(rawKey: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(rawKey.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
