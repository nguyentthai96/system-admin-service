package com.ntt.sysadmin.apipartner.application

import com.ntt.sysadmin.apipartner.adapter.out.persistence.entity.ApiUsageDailyEntity
import com.ntt.sysadmin.apipartner.adapter.out.persistence.entity.RateLimitAuditEntity
import com.ntt.sysadmin.apipartner.adapter.out.persistence.repository.ApiKeyRepository
import com.ntt.sysadmin.apipartner.adapter.out.persistence.repository.ApiUsageDailyRepository
import com.ntt.sysadmin.apipartner.adapter.out.persistence.repository.RateLimitAuditRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * API Usage Tracking Service (FR-012).
 *
 * Uses Redis as a real-time counter (performance), then flushes to DB periodically.
 * Provides:
 * - Real-time request counting via Redis
 * - Daily usage aggregation (flushed every 5 min)
 * - Monthly quota checking
 * - Rate limit audit logging
 * - Usage statistics API
 */
@Service
class ApiUsageService(
    private val apiKeyRepository: ApiKeyRepository,
    private val usageDailyRepository: ApiUsageDailyRepository,
    private val rateLimitAuditRepository: RateLimitAuditRepository,
    private val redisTemplate: StringRedisTemplate
) {

    private val log = LoggerFactory.getLogger(ApiUsageService::class.java)

    companion object {
        private const val USAGE_COUNTER_PREFIX = "api:usage:count:"
        private const val ERROR_COUNTER_PREFIX = "api:usage:error:"
        private const val LATENCY_SUM_PREFIX = "api:usage:latency_sum:"
    }

    /**
     * Record an API request (called on each API call — fast path via Redis).
     */
    fun recordUsage(apiKeyId: Long, isError: Boolean = false, latencyMs: Int = 0) {
        val today = LocalDate.now().toString()
        val countKey = "$USAGE_COUNTER_PREFIX$apiKeyId:$today"
        val latencyKey = "$LATENCY_SUM_PREFIX$apiKeyId:$today"

        redisTemplate.opsForValue().increment(countKey)

        if (isError) {
            val errorKey = "$ERROR_COUNTER_PREFIX$apiKeyId:$today"
            redisTemplate.opsForValue().increment(errorKey)
        }

        if (latencyMs > 0) {
            redisTemplate.opsForValue().increment(latencyKey, latencyMs.toLong())
        }
    }

    /**
     * Check if API key has remaining monthly quota.
     */
    fun checkQuota(apiKeyId: Long): Boolean {
        val apiKey = apiKeyRepository.findById(apiKeyId).orElse(null) ?: return false
        val monthStart = YearMonth.now().atDay(1)
        val usedQuota = usageDailyRepository.sumQuotaUsedSince(apiKeyId, monthStart) ?: 0
        return usedQuota < apiKey.quotaMonthly
    }

    /**
     * Get remaining quota for an API key.
     */
    fun getRemainingQuota(apiKeyId: Long): Map<String, Long> {
        val apiKey = apiKeyRepository.findById(apiKeyId).orElse(null)
            ?: throw IllegalArgumentException("API key not found: $apiKeyId")
        val monthStart = YearMonth.now().atDay(1)
        val usedQuota = usageDailyRepository.sumQuotaUsedSince(apiKeyId, monthStart) ?: 0

        return mapOf(
            "quotaMonthly" to apiKey.quotaMonthly,
            "quotaUsed" to usedQuota,
            "quotaRemaining" to (apiKey.quotaMonthly - usedQuota).coerceAtLeast(0)
        )
    }

    /**
     * Log a rate limit rejection.
     */
    @Transactional
    fun logRateLimitRejection(
        apiKeyId: Long,
        endpoint: String?,
        clientIp: String?,
        limitType: String,
        currentCount: Long?,
        limitValue: Long?
    ) {
        val entity = RateLimitAuditEntity().apply {
            this.apiKeyId = apiKeyId
            this.endpoint = endpoint
            this.clientIp = clientIp
            this.limitType = limitType
            this.currentCount = currentCount
            this.limitValue = limitValue
        }
        rateLimitAuditRepository.save(entity)
    }

    /**
     * Get usage statistics for a partner's API keys.
     */
    fun getUsageStatistics(
        partnerId: Long,
        startDate: LocalDate,
        endDate: LocalDate
    ): Map<String, Any> {
        val apiKeys = apiKeyRepository.findByPartnerId(partnerId)
        val stats = apiKeys.map { key ->
            val dailyUsage = usageDailyRepository.findByApiKeyIdAndUsageDateBetween(key.id!!, startDate, endDate)
            mapOf(
                "apiKeyId" to key.id,
                "keyPrefix" to key.keyPrefix,
                "name" to key.name,
                "totalRequests" to dailyUsage.sumOf { it.requestCount },
                "totalErrors" to dailyUsage.sumOf { it.errorCount },
                "avgLatencyMs" to if (dailyUsage.isNotEmpty()) dailyUsage.map { it.avgLatencyMs }.average().toInt() else 0,
                "dailyBreakdown" to dailyUsage.map { usage ->
                    mapOf(
                        "date" to usage.usageDate.toString(),
                        "requests" to usage.requestCount,
                        "errors" to usage.errorCount,
                        "avgLatencyMs" to usage.avgLatencyMs
                    )
                }
            )
        }

        return mapOf(
            "partnerId" to partnerId,
            "period" to mapOf("start" to startDate.toString(), "end" to endDate.toString()),
            "apiKeys" to stats,
            "totalRequests" to stats.sumOf { (it["totalRequests"] as? Long) ?: 0L }
        )
    }

    /**
     * Get rate limit audit for a partner.
     */
    fun getRateLimitAudit(apiKeyId: Long, page: Int, size: Int): Page<RateLimitAuditEntity> {
        return rateLimitAuditRepository.findByApiKeyIdOrderByRejectedAtDesc(
            apiKeyId, PageRequest.of(page, size)
        )
    }

    // ===============================
    // Scheduled: Flush Redis → DB
    // ===============================

    /**
     * Flush Redis usage counters to DB every 5 minutes.
     */
    @Scheduled(fixedRate = 300_000) // 5 minutes
    @Transactional
    fun flushUsageToDB() {
        val today = LocalDate.now().toString()
        val pattern = "$USAGE_COUNTER_PREFIX*:$today"
        val keys = redisTemplate.keys(pattern) ?: return

        var flushedCount = 0
        keys.forEach { key ->
            try {
                val parts = key.removePrefix(USAGE_COUNTER_PREFIX).split(":")
                if (parts.size < 2) return@forEach

                val apiKeyId = parts[0].toLongOrNull() ?: return@forEach
                val dateStr = parts[1]
                val usageDate = LocalDate.parse(dateStr)

                val requestCount = redisTemplate.opsForValue().get(key)?.toLongOrNull() ?: 0
                val errorKey = "$ERROR_COUNTER_PREFIX${apiKeyId}:$dateStr"
                val errorCount = redisTemplate.opsForValue().get(errorKey)?.toLongOrNull() ?: 0
                val latencyKey = "$LATENCY_SUM_PREFIX${apiKeyId}:$dateStr"
                val latencySum = redisTemplate.opsForValue().get(latencyKey)?.toLongOrNull() ?: 0

                val avgLatency = if (requestCount > 0) (latencySum / requestCount).toInt() else 0

                // Upsert daily record
                val existing = usageDailyRepository.findByApiKeyIdAndUsageDate(apiKeyId, usageDate)
                if (existing != null) {
                    existing.requestCount = requestCount
                    existing.errorCount = errorCount
                    existing.avgLatencyMs = avgLatency
                    existing.quotaUsed = requestCount
                    existing.updatedAt = Instant.now()
                    usageDailyRepository.save(existing)
                } else {
                    val entity = ApiUsageDailyEntity().apply {
                        this.apiKeyId = apiKeyId
                        this.usageDate = usageDate
                        this.requestCount = requestCount
                        this.errorCount = errorCount
                        this.avgLatencyMs = avgLatency
                        this.quotaUsed = requestCount
                    }
                    usageDailyRepository.save(entity)
                }

                flushedCount++
            } catch (e: Exception) {
                log.warn("Failed to flush usage for key: {}", key, e)
            }
        }

        if (flushedCount > 0) {
            log.info("API_USAGE_FLUSH Flushed {} records from Redis to DB", flushedCount)
        }
    }
}
