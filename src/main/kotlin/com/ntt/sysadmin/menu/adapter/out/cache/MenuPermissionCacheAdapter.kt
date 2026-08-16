package com.ntt.sysadmin.menu.adapter.out.cache

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * Redis cache adapter for menu permission trees (FR-010).
 *
 * Key pattern: user:{userId}:menu → JSON serialized menu tree
 * TTL: 5 minutes (BR-MENU-05)
 * Invalidation: On permission change via cache eviction
 */
@Component
class MenuPermissionCacheAdapter(
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper
) {

    private val log = LoggerFactory.getLogger(MenuPermissionCacheAdapter::class.java)

    companion object {
        private const val CACHE_KEY_PREFIX = "user:"
        private const val CACHE_KEY_SUFFIX = ":menu"
        private val CACHE_TTL: Duration = Duration.ofMinutes(5)
    }

    /**
     * Get cached menu tree for a user.
     * Returns null on cache miss.
     */
    fun getCachedMenuTree(userId: Long): String? {
        val key = "$CACHE_KEY_PREFIX$userId$CACHE_KEY_SUFFIX"
        return redisTemplate.opsForValue().get(key)
    }

    /**
     * Cache a menu tree for a user.
     */
    fun cacheMenuTree(userId: Long, menuTreeJson: String) {
        val key = "$CACHE_KEY_PREFIX$userId$CACHE_KEY_SUFFIX"
        redisTemplate.opsForValue().set(key, menuTreeJson, CACHE_TTL)
        log.debug("Cached menu tree for user {} (TTL: {})", userId, CACHE_TTL)
    }

    /**
     * Invalidate menu cache for a specific user.
     */
    fun invalidateUserMenuCache(userId: Long) {
        val key = "$CACHE_KEY_PREFIX$userId$CACHE_KEY_SUFFIX"
        redisTemplate.delete(key)
        log.debug("Invalidated menu cache for user {}", userId)
    }

    /**
     * Invalidate menu cache for all users with a specific role.
     * Called when role permissions change.
     */
    fun invalidateByPattern(pattern: String) {
        val keys = redisTemplate.keys("$CACHE_KEY_PREFIX*$CACHE_KEY_SUFFIX")
        if (keys.isNotEmpty()) {
            redisTemplate.delete(keys)
            log.info("Invalidated {} menu cache entries", keys.size)
        }
    }

    /**
     * Invalidate ALL menu caches (nuclear option).
     * Called when menu structure changes.
     */
    fun invalidateAll() {
        invalidateByPattern("*")
    }

    // ===============================
    // Permission Check Cache (FR-010 auth interceptor)
    // ===============================

    private val PERM_CHECK_TTL: Duration = Duration.ofSeconds(30)

    /**
     * Get cached permission check result.
     */
    fun getCachedPermissionCheck(key: String): String? {
        return redisTemplate.opsForValue().get(key)
    }

    /**
     * Cache a permission check result (TTL 30s).
     */
    fun cachePermissionCheck(key: String, value: String) {
        redisTemplate.opsForValue().set(key, value, PERM_CHECK_TTL)
    }
}
