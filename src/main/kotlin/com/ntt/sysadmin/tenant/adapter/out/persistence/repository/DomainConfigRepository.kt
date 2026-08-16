package com.ntt.sysadmin.tenant.adapter.out.persistence.repository

import com.ntt.sysadmin.tenant.adapter.out.persistence.entity.DomainConfigEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface DomainConfigRepository : JpaRepository<DomainConfigEntity, Long> {
    fun findByDomainId(domainId: Long): DomainConfigEntity?
}
