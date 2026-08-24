package com.ntt.sysadminservice.apipartner.application

import com.ntt.sysadminservice.shared.exception.SysAdminErrorCode
import com.ntt.sysadminservice.shared.exception.SysAdminException
import jakarta.persistence.EntityManager
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration

/**
 * API Partner service — Bucket4j rate limiting, API key lifecycle, IP whitelist (FR-012).
 * Rate limit config persisted in DB and pushed to Redis for Gateway reads.
 * API keys: show-once pattern with SHA-256 hash storage.
 *
 * Key pattern: ntt_pk_{random} (publishable), ntt_sk_{random} (secret).
 * Redis key for rate limit: rate_limit:bucket4j:{keyHash}
 */
@Service
class ApiPartnerService(
    private val entityManager: EntityManager,
    private val redisTemplate: StringRedisTemplate
) {

    private val log = LoggerFactory.getLogger(ApiPartnerService::class.java)
    private val secureRandom = SecureRandom()

    companion object {
        private const val REDIS_RATE_LIMIT_PREFIX = "rate_limit:bucket4j:"
        private const val MAX_KEYS_PER_PARTNER = 5
        private const val KEY_LENGTH = 32
    }

    // ─────────────────────────────────────────────────────────────
    // Rate Limiting (Bucket4j + Redis)
    // ─────────────────────────────────────────────────────────────

    /**
     * Configure rate limit for a partner and push to Redis for Gateway consumption.
     * Gateway reads from Redis key `rate_limit:bucket4j:{keyHash}` at runtime.
     */
    @Transactional
    fun configureRateLimit(partnerId: Long, requestsPerMinute: Int, burstCapacity: Int) {
        validatePartnerExists(partnerId)

        entityManager.createNativeQuery("""
            UPDATE api_partners 
            SET rate_limit_requests_per_minute = :rpm, rate_limit_burst = :burst, updated_at = NOW()
            WHERE id = :id AND active = true
        """)
            .setParameter("rpm", requestsPerMinute)
            .setParameter("burst", burstCapacity)
            .setParameter("id", partnerId)
            .executeUpdate()

        // Push rate limit config to Redis for all active keys of this partner
        pushRateLimitToRedis(partnerId, requestsPerMinute, burstCapacity)

        log.info("Rate limit configured: partnerId={}, rpm={}, burst={}", partnerId, requestsPerMinute, burstCapacity)
    }

    /**
     * Push rate limit config to Redis for all active API keys of a partner.
     * Gateway reads this at request time via Bucket4j ProxyManager.
     */
    private fun pushRateLimitToRedis(partnerId: Long, requestsPerMinute: Int, burstCapacity: Int) {
        @Suppress("UNCHECKED_CAST")
        val keyHashes = entityManager
            .createNativeQuery("SELECT key_hash FROM api_keys WHERE partner_id = :id AND active = true AND revoked = false")
            .setParameter("id", partnerId)
            .resultList as List<String>

        val config = """{"requestsPerMinute":$requestsPerMinute,"burstCapacity":$burstCapacity}"""
        keyHashes.forEach { keyHash ->
            try {
                redisTemplate.opsForValue().set(
                    "$REDIS_RATE_LIMIT_PREFIX$keyHash",
                    config,
                    Duration.ofHours(24)
                )
            } catch (e: Exception) {
                log.warn("Failed to push rate limit to Redis for keyHash={}: {}", keyHash, e.message)
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // API Key Lifecycle (FR-012)
    // ─────────────────────────────────────────────────────────────

    /**
     * Generate a new API key for a partner (show-once pattern).
     * Returns the plain key once — only the SHA-256 hash is stored.
     *
     * @param partnerId The partner to generate the key for
     * @param keyType "pk" (publishable) or "sk" (secret)
     * @return Map with plainKey (show-once) and keyId
     */
    @Transactional
    fun generateApiKey(partnerId: Long, keyType: String = "sk"): Map<String, Any> {
        validatePartnerExists(partnerId)

        // Check max keys limit
        val activeKeyCount = entityManager
            .createNativeQuery("SELECT COUNT(*) FROM api_keys WHERE partner_id = :id AND active = true AND revoked = false")
            .setParameter("id", partnerId)
            .singleResult as Number

        if (activeKeyCount.toInt() >= MAX_KEYS_PER_PARTNER) {
            throw SysAdminException(SysAdminErrorCode.RATE_LIMIT_EXCEEDED,
                "Maximum API keys ($MAX_KEYS_PER_PARTNER) reached for partner $partnerId")
        }

        // Generate key: ntt_pk_{random} or ntt_sk_{random}
        val prefix = if (keyType == "pk") "ntt_pk_" else "ntt_sk_"
        val randomPart = generateSecureRandom(KEY_LENGTH)
        val plainKey = "$prefix$randomPart"

        // Store only the hash
        val keyHash = sha256Hash(plainKey)

        entityManager.createNativeQuery("""
            INSERT INTO api_keys (id, partner_id, key_hash, key_type, key_prefix, active, revoked, created_at)
            VALUES (nextval('snowflake_seq'), :partnerId, :hash, :type, :prefix, true, false, NOW())
        """)
            .setParameter("partnerId", partnerId)
            .setParameter("hash", keyHash)
            .setParameter("type", keyType)
            .setParameter("prefix", prefix)
            .executeUpdate()

        // Get the generated key ID
        val keyId = entityManager
            .createNativeQuery("SELECT id FROM api_keys WHERE key_hash = :hash")
            .setParameter("hash", keyHash)
            .singleResult as Number

        // Push rate limit config for new key
        @Suppress("UNCHECKED_CAST")
        val partnerConfig = entityManager
            .createNativeQuery("SELECT rate_limit_requests_per_minute, rate_limit_burst FROM api_partners WHERE id = :id")
            .setParameter("id", partnerId)
            .singleResult as Array<Any?>

        val rpm = (partnerConfig[0] as? Number)?.toInt() ?: 100
        val burst = (partnerConfig[1] as? Number)?.toInt() ?: 20
        val config = """{"requestsPerMinute":$rpm,"burstCapacity":$burst}"""
        try {
            redisTemplate.opsForValue().set("$REDIS_RATE_LIMIT_PREFIX$keyHash", config, Duration.ofHours(24))
        } catch (e: Exception) {
            log.warn("Failed to push rate limit config for new key: {}", e.message)
        }

        log.info("API key generated: partnerId={}, keyType={}, keyId={}", partnerId, keyType, keyId)

        return mapOf(
            "keyId" to keyId.toLong(),
            "plainKey" to plainKey, // Show-once — NOT stored
            "keyType" to keyType,
            "prefix" to prefix
        )
    }

    /**
     * Rotate an API key — revoke old, generate new.
     */
    @Transactional
    fun rotateApiKey(partnerId: Long, keyId: Long): Map<String, Any> {
        validatePartnerExists(partnerId)

        // Revoke old key
        val updated = entityManager.createNativeQuery("""
            UPDATE api_keys SET revoked = true, revoked_at = NOW(), active = false 
            WHERE id = :keyId AND partner_id = :partnerId AND active = true
        """)
            .setParameter("keyId", keyId)
            .setParameter("partnerId", partnerId)
            .executeUpdate()

        if (updated == 0) {
            throw SysAdminException(SysAdminErrorCode.PARTNER_NOT_FOUND, "API key not found or already revoked: $keyId")
        }

        // Remove old key from Redis rate limit
        @Suppress("UNCHECKED_CAST")
        val oldHash = try {
            entityManager
                .createNativeQuery("SELECT key_hash FROM api_keys WHERE id = :id")
                .setParameter("id", keyId)
                .singleResult as? String
        } catch (e: Exception) { null }

        if (oldHash != null) {
            redisTemplate.delete("$REDIS_RATE_LIMIT_PREFIX$oldHash")
        }

        log.info("API key rotated: partnerId={}, oldKeyId={}", partnerId, keyId)

        // Generate new key (same type)
        @Suppress("UNCHECKED_CAST")
        val keyType = try {
            entityManager
                .createNativeQuery("SELECT key_type FROM api_keys WHERE id = :id")
                .setParameter("id", keyId)
                .singleResult as? String ?: "sk"
        } catch (e: Exception) { "sk" }

        return generateApiKey(partnerId, keyType)
    }

    /**
     * Revoke an API key permanently.
     */
    @Transactional
    fun revokeApiKey(keyId: Long) {
        @Suppress("UNCHECKED_CAST")
        val keyHash = try {
            entityManager
                .createNativeQuery("SELECT key_hash FROM api_keys WHERE id = :id AND active = true")
                .setParameter("id", keyId)
                .singleResult as? String
        } catch (e: Exception) {
            throw SysAdminException(SysAdminErrorCode.PARTNER_NOT_FOUND, "API key not found: $keyId")
        }

        entityManager.createNativeQuery("""
            UPDATE api_keys SET revoked = true, revoked_at = NOW(), active = false 
            WHERE id = :id
        """)
            .setParameter("id", keyId)
            .executeUpdate()

        // Remove from Redis
        if (keyHash != null) {
            redisTemplate.delete("$REDIS_RATE_LIMIT_PREFIX$keyHash")
        }

        log.info("API key revoked: keyId={}", keyId)
    }

    // ─────────────────────────────────────────────────────────────
    // IP Whitelist (FR-012)
    // ─────────────────────────────────────────────────────────────

    /**
     * Update IP whitelist for an API partner.
     * Supports IPv4 addresses and CIDR notation (e.g., 192.168.1.0/24).
     */
    @Transactional
    fun updateIpWhitelist(partnerId: Long, ipAddresses: List<String>) {
        validatePartnerExists(partnerId)

        // Validate IP format (IPv4 + optional CIDR)
        val ipRegex = Regex("""^(\d{1,3}\.){3}\d{1,3}(/\d{1,2})?$""")
        ipAddresses.forEach { ip ->
            if (!ipRegex.matches(ip)) {
                throw SysAdminException(SysAdminErrorCode.GENERAL_ERROR, "Invalid IP address format: $ip")
            }
        }

        val jsonArray = "[${ipAddresses.joinToString(",") { "\"$it\"" }}]"

        entityManager
            .createNativeQuery("UPDATE api_partners SET ip_whitelist = :ips::jsonb, updated_at = NOW() WHERE id = :id")
            .setParameter("ips", jsonArray)
            .setParameter("id", partnerId)
            .executeUpdate()

        log.info("IP whitelist updated for partner {}: {}", partnerId, ipAddresses)
    }

    /**
     * Validate that a request IP is in the partner's whitelist.
     * Supports exact match and CIDR range check.
     */
    fun isIpWhitelisted(partnerId: Long, requestIp: String): Boolean {
        @Suppress("UNCHECKED_CAST")
        val result = try {
            entityManager
                .createNativeQuery("""
                    SELECT ip_whitelist::text FROM api_partners 
                    WHERE id = :id AND active = true
                """)
                .setParameter("id", partnerId)
                .singleResult as? String
        } catch (e: Exception) {
            return true // If no whitelist configured, allow all
        }

        if (result.isNullOrBlank() || result == "[]") return true

        // Check exact match first
        if (result.contains(requestIp)) return true

        // CIDR matching — parse each entry and check
        return checkCidrMatch(result, requestIp)
    }

    // ─────────────────────────────────────────────────────────────
    // Usage Dashboard
    // ─────────────────────────────────────────────────────────────

    /**
     * Get aggregated API usage statistics for a partner.
     */
    fun getUsageStatistics(partnerId: Long): Map<String, Any> {
        @Suppress("UNCHECKED_CAST")
        val result = try {
            entityManager
                .createNativeQuery("""
                    SELECT COUNT(*) as total_calls,
                           COUNT(CASE WHEN status_code >= 200 AND status_code < 300 THEN 1 END) as success_count,
                           COUNT(CASE WHEN status_code >= 400 THEN 1 END) as error_count,
                           AVG(response_time_ms) as avg_response_time
                    FROM api_usage_logs 
                    WHERE partner_id = :partnerId
                    AND created_at >= NOW() - INTERVAL '30 days'
                """)
                .setParameter("partnerId", partnerId)
                .singleResult as Array<Any?>
        } catch (e: Exception) {
            log.warn("Failed to aggregate API usage for partner {}: {}", partnerId, e.message)
            return mapOf("totalCalls" to 0, "successCount" to 0, "errorCount" to 0, "avgResponseTimeMs" to 0)
        }

        return mapOf(
            "totalCalls" to (result[0] ?: 0),
            "successCount" to (result[1] ?: 0),
            "errorCount" to (result[2] ?: 0),
            "avgResponseTimeMs" to (result[3] ?: 0)
        )
    }

    // ─────────────────────────────────────────────────────────────
    // Private Helpers
    // ─────────────────────────────────────────────────────────────

    private fun validatePartnerExists(partnerId: Long) {
        val count = entityManager
            .createNativeQuery("SELECT COUNT(*) FROM api_partners WHERE id = :id AND active = true")
            .setParameter("id", partnerId)
            .singleResult as Number
        if (count.toInt() == 0) {
            throw SysAdminException(SysAdminErrorCode.PARTNER_NOT_FOUND, "API partner not found: $partnerId")
        }
    }

    private fun generateSecureRandom(length: Int): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return (1..length).map { chars[secureRandom.nextInt(chars.length)] }.joinToString("")
    }

    private fun sha256Hash(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    private fun checkCidrMatch(ipListJson: String, requestIp: String): Boolean {
        // Parse JSON array of IPs
        val cleanList = ipListJson.trim().removeSurrounding("[", "]")
        val ips = cleanList.split(",").map { it.trim().removeSurrounding("\"") }

        return ips.any { entry ->
            if (entry.contains("/")) {
                isIpInCidr(requestIp, entry)
            } else {
                entry == requestIp
            }
        }
    }

    private fun isIpInCidr(ip: String, cidr: String): Boolean {
        return try {
            val parts = cidr.split("/")
            val cidrIp = parts[0]
            val prefixLen = parts[1].toInt()

            val ipNum = ipToLong(ip)
            val cidrNum = ipToLong(cidrIp)
            val mask = (-1L shl (32 - prefixLen)) and 0xFFFFFFFFL

            (ipNum and mask) == (cidrNum and mask)
        } catch (e: Exception) {
            false
        }
    }

    private fun ipToLong(ip: String): Long {
        val octets = ip.split(".")
        return (octets[0].toLong() shl 24) or (octets[1].toLong() shl 16) or
                (octets[2].toLong() shl 8) or octets[3].toLong()
    }
}
