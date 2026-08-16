package com.ntt.sysadmin.apipartner.adapter.out.persistence.repository

import com.ntt.sysadmin.apipartner.adapter.out.persistence.entity.ApiUsageDailyEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
interface ApiUsageDailyRepository : JpaRepository<ApiUsageDailyEntity, Long> {

    fun findByApiKeyIdAndUsageDate(apiKeyId: Long, usageDate: LocalDate): ApiUsageDailyEntity?

    fun findByApiKeyIdAndUsageDateBetween(apiKeyId: Long, startDate: LocalDate, endDate: LocalDate): List<ApiUsageDailyEntity>

    @Query("SELECT SUM(u.quotaUsed) FROM ApiUsageDailyEntity u WHERE u.apiKeyId = :apiKeyId AND u.usageDate >= :monthStart")
    fun sumQuotaUsedSince(apiKeyId: Long, monthStart: LocalDate): Long?
}
