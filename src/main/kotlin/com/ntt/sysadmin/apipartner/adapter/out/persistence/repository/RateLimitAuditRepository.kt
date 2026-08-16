package com.ntt.sysadmin.apipartner.adapter.out.persistence.repository

import com.ntt.sysadmin.apipartner.adapter.out.persistence.entity.RateLimitAuditEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface RateLimitAuditRepository : JpaRepository<RateLimitAuditEntity, Long> {

    fun findByApiKeyIdOrderByRejectedAtDesc(apiKeyId: Long, pageable: Pageable): Page<RateLimitAuditEntity>

    fun countByApiKeyIdAndRejectedAtAfter(apiKeyId: Long, after: Instant): Long
}
