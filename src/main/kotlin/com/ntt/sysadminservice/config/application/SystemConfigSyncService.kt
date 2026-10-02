package com.ntt.sysadminservice.config.application

import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service

@Service
class SystemConfigSyncService(
    private val redisTemplate: StringRedisTemplate
) {
    private val log = LoggerFactory.getLogger(SystemConfigSyncService::class.java)

    companion object {
        const val REDIS_HASH_KEY = "system:config:auth_login"
    }

    fun syncToRedis(key: String, value: String) {
        if (key.startsWith("password.") || key.startsWith("mfa.") || key.startsWith("session.")) {
            try {
                redisTemplate.opsForHash<String, String>().put(REDIS_HASH_KEY, key, value)
                log.info("Synced config {} to Redis hash {}", key, REDIS_HASH_KEY)
            } catch (ex: Exception) {
                log.error("Failed to sync config {} to Redis", key, ex)
            }
        }
    }
}
