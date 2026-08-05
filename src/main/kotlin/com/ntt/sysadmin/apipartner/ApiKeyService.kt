package com.ntt.sysadmin.apipartner

import org.springframework.stereotype.Service
import org.springframework.data.redis.core.StringRedisTemplate

@Service
class ApiKeyService(
    private val redisTemplate: StringRedisTemplate
) {
    fun generateApiKey(partnerId: String): String {
        val apiKey = "ntt_pk_${java.util.UUID.randomUUID().toString().replace("-", "")}"
        // TODO: Hash and store in DB
        return apiKey
    }

    fun syncRateLimitToRedis(apiKeyHash: String, limitPerMinute: Int) {
        val redisKey = "rate_limit:bucket4j:$apiKeyHash"
        // Populate Bucket4j token bucket config to Redis for API Gateway to consume
        redisTemplate.opsForValue().set(redisKey, limitPerMinute.toString())
    }
}
