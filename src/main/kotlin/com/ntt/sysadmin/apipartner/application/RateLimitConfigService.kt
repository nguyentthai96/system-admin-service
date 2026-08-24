package com.ntt.sysadmin.apipartner.application

import com.ntt.sysadmin.apipartner.adapter.out.persistence.entity.ApiKeyEntity
import com.ntt.sysadmin.apipartner.adapter.out.persistence.repository.ApiKeyRepository
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RateLimitConfigService(
    private val apiKeyRepository: ApiKeyRepository,
    private val redisTemplate: StringRedisTemplate
) {
    private val log = LoggerFactory.getLogger(RateLimitConfigService::class.java)

    companion object {
        private const val REDIS_RATE_LIMIT_PREFIX = "rate_limit:bucket4j:"
    }

    @Transactional
    fun configureRateLimit(apiKeyId: Long, rateLimitPerSecond: Int, rateLimitPerMinute: Int, rateLimitPerDay: Long): ApiKeyEntity {
        val apiKey = apiKeyRepository.findById(apiKeyId)
            .orElseThrow { IllegalArgumentException("API key not found: $apiKeyId") }
        apiKey.rateLimitPerSecond = rateLimitPerSecond
        apiKey.rateLimitPerMinute = rateLimitPerMinute
        apiKey.rateLimitPerDay = rateLimitPerDay
        val saved = apiKeyRepository.save(apiKey)
        pushRateLimitToRedis(apiKey.keyHash, rateLimitPerSecond, rateLimitPerMinute, rateLimitPerDay)
        log.info("Rate limit configured for API key {}: {}/s, {}/min, {}/day", apiKeyId, rateLimitPerSecond, rateLimitPerMinute, rateLimitPerDay)
        return saved
    }

    @Transactional
    fun configurePartnerRateLimit(partnerId: Long, rateLimitPerSecond: Int, rateLimitPerMinute: Int, rateLimitPerDay: Long) {
        val keys = apiKeyRepository.findByPartnerIdAndStatus(partnerId, ApiKeyEntity.STATUS_ACTIVE)
        keys.forEach { apiKey ->
            apiKey.rateLimitPerSecond = rateLimitPerSecond
            apiKey.rateLimitPerMinute = rateLimitPerMinute
            apiKey.rateLimitPerDay = rateLimitPerDay
            apiKeyRepository.save(apiKey)
            pushRateLimitToRedis(apiKey.keyHash, rateLimitPerSecond, rateLimitPerMinute, rateLimitPerDay)
        }
        log.info("Rate limit configured for partner {} ({} keys)", partnerId, keys.size)
    }

    fun getRateLimitConfig(apiKeyId: Long): Map<String, Any> {
        val apiKey = apiKeyRepository.findById(apiKeyId)
            .orElseThrow { IllegalArgumentException("API key not found: $apiKeyId") }
        val redisKey = "$REDIS_RATE_LIMIT_PREFIX${apiKey.keyHash}"
        val redisConfig = redisTemplate.opsForHash<String, String>().entries(redisKey)
        return mapOf("apiKeyId" to apiKeyId, "rateLimitPerSecond" to apiKey.rateLimitPerSecond,
            "rateLimitPerMinute" to apiKey.rateLimitPerMinute, "rateLimitPerDay" to apiKey.rateLimitPerDay,
            "redisSynced" to redisConfig.isNotEmpty())
    }

    fun pushRateLimitToRedis(keyHash: String, limitPerSecond: Int, limitPerMinute: Int, limitPerDay: Long) {
        val redisKey = "$REDIS_RATE_LIMIT_PREFIX$keyHash"
        redisTemplate.opsForHash<String, String>().putAll(redisKey, mapOf(
            "limitPerSecond" to limitPerSecond.toString(), "limitPerMinute" to limitPerMinute.toString(),
            "limitPerDay" to limitPerDay.toString()))
    }

    fun removeRateLimitFromRedis(keyHash: String) {
        redisTemplate.delete("$REDIS_RATE_LIMIT_PREFIX$keyHash")
    }
}
