package com.ntt.sysadmin.versioning.storage.repository

import com.ntt.sysadmin.versioning.storage.entity.ConfigMilestoneEntity
import com.ntt.sysadmin.versioning.storage.entity.ConfigSnapshotEntity
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface ConfigMilestoneRepository : JpaRepository<ConfigMilestoneEntity, String> {
    fun findAllByOrderByCreatedAtDesc(): List<ConfigMilestoneEntity>
}

@Repository
interface ConfigSnapshotRepository : JpaRepository<ConfigSnapshotEntity, String> {

    fun findAllByMilestoneId(milestoneId: String): List<ConfigSnapshotEntity>

    fun findAllByDomainNameOrderByCreatedAtDesc(domainName: String, pageable: Pageable): List<ConfigSnapshotEntity>

    @Query("SELECT s FROM ConfigSnapshotEntity s WHERE s.domainName = :domainName ORDER BY s.createdAt DESC LIMIT 1")
    fun findLatestByDomainName(@Param("domainName") domainName: String): ConfigSnapshotEntity?
}
