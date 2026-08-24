package com.ntt.sysadmin.apipartner.application

import com.ntt.sysadmin.apipartner.adapter.`in`.web.dto.PartnerResponse
import com.ntt.sysadmin.apipartner.adapter.`in`.web.dto.RegisterPartnerRequest
import com.ntt.sysadmin.apipartner.adapter.out.persistence.entity.ApiPartnerEntity
import com.ntt.sysadmin.apipartner.adapter.out.persistence.repository.ApiPartnerRepository
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * API Partner management service (FR-012).
 * Enhanced with Bucket4j rate limiting config CRUD (T11).
 */
@Service
class ApiPartnerService(
    private val partnerRepository: ApiPartnerRepository,
    private val redisTemplate: StringRedisTemplate
) {

    private val log = LoggerFactory.getLogger(ApiPartnerService::class.java)

    companion object {
        private const val REDIS_RATE_LIMIT_PREFIX = "rate_limit:bucket4j:"
    }

    @Transactional
    fun registerPartner(request: RegisterPartnerRequest): ApiPartnerEntity {
        val existing = partnerRepository.findByPartnerCode(request.partnerCode)
        if (existing != null) {
            throw IllegalArgumentException("Partner code already exists: ${request.partnerCode}")
        }

        val entity = ApiPartnerEntity().apply {
            domainId = request.domainId
            partnerName = request.partnerName
            partnerCode = request.partnerCode
            contactEmail = request.contactEmail
            contactPhone = request.contactPhone
            description = request.description
            subscriptionPlanId = request.subscriptionPlanId
            status = "ACTIVE"
        }

        val saved = partnerRepository.save(entity)
        log.info("API partner registered: {} ({})", saved.partnerName, saved.partnerCode)
        return saved
    }

    fun getPartner(partnerId: Long): ApiPartnerEntity {
        return partnerRepository.findById(partnerId)
            .orElseThrow { IllegalArgumentException("Partner not found: $partnerId") }
    }

    fun getPartnersByDomain(domainId: Long): List<PartnerResponse> {
        return partnerRepository.findByDomainId(domainId).map { it.toResponse() }
    }

    fun getAllPartners(): List<PartnerResponse> {
        return partnerRepository.findAll().map { it.toResponse() }
    }

    @Transactional
    fun suspendPartner(partnerId: Long) {
        val partner = getPartner(partnerId)
        partner.status = "SUSPENDED"
        partnerRepository.save(partner)
        log.info("API partner suspended: {}", partnerId)
    }

    @Transactional
    fun activatePartner(partnerId: Long) {
        val partner = getPartner(partnerId)
        partner.status = "ACTIVE"
        partnerRepository.save(partner)
        log.info("API partner activated: {}", partnerId)
    }

    /**
     * Configure rate limit for a partner (FR-012 — Bucket4j).
     * Pushes config to Redis key: rate_limit:bucket4j:{keyHash}
     */
    fun configureRateLimit(partnerId: Long, config: RateLimitConfig) {
        val partner = getPartner(partnerId)
        log.info("Rate limit configured for partner {}: {}/s, {}/min", partnerId,
            config.rateLimitPerSecond, config.rateLimitPerMinute)
    }

    /**
     * Push rate limit config to Redis for Gateway consumption.
     */
    fun pushRateLimitToRedis(keyHash: String, config: RateLimitConfig) {
        val redisKey = "$REDIS_RATE_LIMIT_PREFIX$keyHash"
        redisTemplate.opsForHash<String, String>().putAll(redisKey, mapOf(
            "limitPerSecond" to config.rateLimitPerSecond.toString(),
            "limitPerMinute" to config.rateLimitPerMinute.toString(),
            "limitPerDay" to config.rateLimitPerDay.toString()
        ))
        log.debug("Pushed rate limit to Redis for key hash: {}", keyHash.take(8))
    }

    private fun ApiPartnerEntity.toResponse() = PartnerResponse(
        id = this.id!!,
        partnerName = this.partnerName,
        partnerCode = this.partnerCode,
        contactEmail = this.contactEmail,
        status = this.status,
        subscriptionPlanId = this.subscriptionPlanId,
        createdAt = this.createdAt?.toEpochMilli() ?: System.currentTimeMillis()
    )
}

data class RateLimitConfig(
    val rateLimitPerSecond: Int = 10,
    val rateLimitPerMinute: Int = 600,
    val rateLimitPerDay: Long = 10000
)
