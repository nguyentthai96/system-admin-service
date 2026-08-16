package com.ntt.sysadmin.tenant.adapter.out.persistence.repository

import com.ntt.sysadmin.tenant.adapter.out.persistence.entity.DomainConfigHistoryEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface DomainConfigHistoryRepository : JpaRepository<DomainConfigHistoryEntity, Long> {

    fun findByDomainIdOrderByVersionDesc(domainId: Long): List<DomainConfigHistoryEntity>

    fun findTopByDomainIdOrderByVersionDesc(domainId: Long): DomainConfigHistoryEntity?
}
