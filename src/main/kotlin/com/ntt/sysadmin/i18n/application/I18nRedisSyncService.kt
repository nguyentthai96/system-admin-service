package com.ntt.sysadmin.i18n.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.ntt.sysadmin.versioning.domain.entity.I18nMessageRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Duration

/**
 * Service responsible for synchronizing i18n messages from database to Redis.
 *
 * Functions:
 * - Full sync on startup (with retry on failure)
 * - Single message sync (after CRUD operations)
 * - Pub/Sub invalidation publishing
 * - Version counter management
 *
 * Redis key schema:
 * - i18n:data:{locale}  → Hash (field=code, value=message) with TTL 10 days
 * - i18n:version         → String (monotonic counter)
 * - i18n:invalidation    → Pub/Sub channel
 *
 * FR-002: Redis L2 Cache — Hash per locale
 * FR-006: Version counter (ETag)
 * FR-008: Startup full sync DB → Redis
 * FR-012: Startup sync retry when Redis down
 */
@Service
class I18nRedisSyncService(
    private val repository: I18nMessageRepository,
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Volatile
    private var synced = false

    companion object {
        private const val HASH_KEY_PREFIX = "i18n:data:"
        private const val VERSION_KEY = "i18n:version"
        private const val INVALIDATION_CHANNEL = "i18n:invalidation"
        private val HASH_TTL: Duration = Duration.ofDays(10)
    }

    /**
     * Full sync all active i18n messages from DB to Redis Hashes.
     * Called on application startup and retry.
     */
    fun fullSync() {
        val messages = repository.findAllByIsActiveTrue()
        if (messages.isEmpty()) {
            log.warn("No active i18n messages found in database — Redis sync skipped")
            synced = true
            return
        }

        // Group by locale and sync each locale hash
        val grouped = messages.groupBy { it.locale }
        var totalSynced = 0

        for ((locale, localeMessages) in grouped) {
            val hashKey = "$HASH_KEY_PREFIX$locale"
            val map = localeMessages.associate { it.code to it.message }

            redisTemplate.opsForHash<String, String>().putAll(hashKey, map)
            redisTemplate.expire(hashKey, HASH_TTL)
            totalSynced += map.size
        }

        // Increment version
        redisTemplate.opsForValue().increment(VERSION_KEY)

        synced = true
        log.info("Synced {} i18n messages to Redis ({} locales)", totalSynced, grouped.size)
    }

    /**
     * Sync a single message to Redis after create/update.
     */
    fun syncSingle(code: String, locale: String, message: String) {
        try {
            val hashKey = "$HASH_KEY_PREFIX$locale"
            redisTemplate.opsForHash<String, String>().put(hashKey, code, message)
            redisTemplate.expire(hashKey, HASH_TTL)
            redisTemplate.opsForValue().increment(VERSION_KEY)

            publishInvalidation("SINGLE", code, locale)
            log.debug("Synced single i18n message: {}:{}", code, locale)
        } catch (e: Exception) {
            log.error("Failed to sync single i18n message: {}:{}", code, locale, e)
        }
    }

    /**
     * Delete a single message from Redis after deactivation/deletion.
     */
    fun deleteSingle(code: String, locale: String) {
        try {
            val hashKey = "$HASH_KEY_PREFIX$locale"
            redisTemplate.opsForHash<String, String>().delete(hashKey, code)
            redisTemplate.opsForValue().increment(VERSION_KEY)

            publishInvalidation("SINGLE", code, locale)
            log.debug("Deleted single i18n message from Redis: {}:{}", code, locale)
        } catch (e: Exception) {
            log.error("Failed to delete single i18n message: {}:{}", code, locale, e)
        }
    }

    /**
     * Publish full invalidation event (used after batch operations or rollback).
     */
    fun publishFullInvalidation() {
        try {
            redisTemplate.opsForValue().increment(VERSION_KEY)
            publishInvalidation("FULL", null, null)
            log.info("Published full i18n cache invalidation")
        } catch (e: Exception) {
            log.error("Failed to publish full i18n invalidation", e)
        }
    }

    /**
     * Startup sync — try-catch with warning log.
     * If Redis is down, service still starts. Retry via @Scheduled.
     */
    @EventListener(ApplicationReadyEvent::class)
    fun onApplicationReady() {
        try {
            fullSync()
        } catch (e: Exception) {
            log.warn("I18n Redis sync failed on startup — will retry in 3 minutes: {}", e.message)
            synced = false
        }
    }

    /**
     * Scheduled retry for sync if startup sync failed.
     * Retries every 3 minutes until successful.
     */
    @Scheduled(fixedDelay = 180_000, initialDelay = 180_000)
    fun scheduledRetrySync() {
        if (synced) return
        try {
            log.info("Retrying i18n Redis sync...")
            fullSync()
        } catch (e: Exception) {
            log.warn("I18n Redis sync retry failed — will retry again: {}", e.message)
        }
    }

    private fun publishInvalidation(type: String, code: String?, locale: String?) {
        val payload = buildMap {
            put("type", type)
            if (code != null) put("code", code)
            if (locale != null) put("locale", locale)
        }
        val json = objectMapper.writeValueAsString(payload)
        redisTemplate.convertAndSend(INVALIDATION_CHANNEL, json)
    }
}
