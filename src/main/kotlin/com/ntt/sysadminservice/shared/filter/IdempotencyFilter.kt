package com.ntt.sysadminservice.shared.filter

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.util.ContentCachingResponseWrapper
import java.time.Duration

/**
 * Idempotency filter for system-admin-service (FR-017).
 * Replicated from auth-service IdempotencyFilter pattern.
 * Reads X-Idempotency-Key header, checks Redis for cached response.
 * If duplicate request, returns cached response.
 * If new request, processes and caches the response (TTL 24h).
 */
@Component
class IdempotencyFilter(
    private val redisTemplate: StringRedisTemplate
) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(IdempotencyFilter::class.java)

    companion object {
        private const val IDEMPOTENCY_HEADER = "X-Idempotency-Key"
        private const val REDIS_PREFIX = "idempotency:system-admin-service:"
        private val TTL = Duration.ofHours(24)
    }

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        // Only apply to mutating methods (POST, PUT, PATCH)
        return request.method !in setOf("POST", "PUT", "PATCH")
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val idempotencyKey = request.getHeader(IDEMPOTENCY_HEADER)

        // If no idempotency key, proceed normally
        if (idempotencyKey.isNullOrBlank()) {
            filterChain.doFilter(request, response)
            return
        }

        val redisKey = "$REDIS_PREFIX$idempotencyKey"

        // Check for cached response
        val cachedResponse = try {
            redisTemplate.opsForValue().get(redisKey)
        } catch (e: Exception) {
            log.warn("Redis read failed for idempotency check: {}", e.message)
            null
        }

        if (cachedResponse != null) {
            log.info("Idempotent request detected: key={}", idempotencyKey)
            response.contentType = "application/json"
            response.status = HttpServletResponse.SC_OK
            response.setHeader("X-Idempotent-Replay", "true")
            response.writer.write(cachedResponse)
            return
        }

        // Process request and cache response
        val responseWrapper = ContentCachingResponseWrapper(response)
        filterChain.doFilter(request, responseWrapper)

        // Cache successful responses only
        if (responseWrapper.status in 200..299) {
            val responseBody = String(responseWrapper.contentAsByteArray)
            if (responseBody.isNotBlank()) {
                try {
                    redisTemplate.opsForValue().set(redisKey, responseBody, TTL)
                    log.debug("Response cached for idempotency key: {}", idempotencyKey)
                } catch (e: Exception) {
                    log.warn("Failed to cache idempotent response: {}", e.message)
                }
            }
        }

        responseWrapper.copyBodyToResponse()
    }
}
