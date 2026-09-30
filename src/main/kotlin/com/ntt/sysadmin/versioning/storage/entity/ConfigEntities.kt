package com.ntt.sysadmin.versioning.storage.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

@Entity
@Table(name = "sys_config_milestone")
class ConfigMilestoneEntity {

    @Id
    @Column(name = "id", length = 64, nullable = false)
    var id: String = ""

    @Column(name = "name", length = 255, nullable = false)
    var name: String = ""

    @Column(name = "description", columnDefinition = "TEXT")
    var description: String? = null

    @Column(name = "created_by", length = 100)
    var createdBy: String? = null

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now()

    @Column(name = "status", length = 30, nullable = false)
    var status: String = "ACTIVE"
}

@Entity
@Table(name = "sys_config_snapshot")
class ConfigSnapshotEntity {

    @Id
    @Column(name = "id", length = 64, nullable = false)
    var id: String = ""

    @Column(name = "milestone_id", length = 64)
    var milestoneId: String? = null

    @Column(name = "domain_name", length = 50, nullable = false)
    var domainName: String = ""

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "state_payload", columnDefinition = "jsonb", nullable = false)
    var statePayload: String = ""

    @Column(name = "checksum_sha256", length = 64, nullable = false)
    var checksumSha256: String = ""

    @Column(name = "record_count", nullable = false)
    var recordCount: Int = 0

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now()
}
