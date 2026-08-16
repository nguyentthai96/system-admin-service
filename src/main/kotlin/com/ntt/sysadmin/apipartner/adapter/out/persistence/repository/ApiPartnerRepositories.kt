package com.ntt.sysadmin.apipartner.adapter.out.persistence.repository

import com.ntt.sysadmin.apipartner.adapter.out.persistence.entity.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface SubscriptionPlanRepository : JpaRepository<SubscriptionPlanEntity, Long> {
    fun findByCode(code: String): SubscriptionPlanEntity?
    fun findByStatus(status: String): List<SubscriptionPlanEntity>
}

@Repository
interface ApiPartnerRepository : JpaRepository<ApiPartnerEntity, Long> {
    fun findByPartnerCode(partnerCode: String): ApiPartnerEntity?
    fun findByDomainId(domainId: Long): List<ApiPartnerEntity>
    fun findByStatus(status: String): List<ApiPartnerEntity>
}

@Repository
interface ApiKeyRepository : JpaRepository<ApiKeyEntity, Long> {
    fun findByKeyHash(keyHash: String): ApiKeyEntity?
    fun findByPartnerId(partnerId: Long): List<ApiKeyEntity>
    fun findByPartnerIdAndStatus(partnerId: Long, status: String): List<ApiKeyEntity>

    @Query("SELECT k FROM ApiKeyEntity k WHERE k.keyPrefix = :prefix AND k.status = 'ACTIVE'")
    fun findActiveByPrefix(prefix: String): List<ApiKeyEntity>
}

@Repository
interface ApiUsageLogRepository : JpaRepository<ApiUsageLogEntity, Long> {
    fun findByPartnerIdAndRequestAtBetween(partnerId: Long, from: Long, to: Long): List<ApiUsageLogEntity>

    @Query("SELECT COUNT(l) FROM ApiUsageLogEntity l WHERE l.apiKeyId = :keyId AND l.requestAt >= :since")
    fun countByApiKeyIdSince(keyId: Long, since: Long): Long
}
