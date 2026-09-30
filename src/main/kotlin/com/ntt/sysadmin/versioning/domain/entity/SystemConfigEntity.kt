package com.ntt.sysadmin.versioning.domain.entity

import com.ntt.basecore.model.id.SnowflakePersistentAuditableEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Entity
@Table(name = "system_configs")
class SystemConfigEntity : SnowflakePersistentAuditableEntity() {

    @Column(name = "domain_id", nullable = false)
    var domainId: Long = 0L

    @Column(name = "config_key", length = 100, nullable = false)
    var configKey: String = ""

    @Column(name = "config_value", length = 4000, nullable = false)
    var configValue: String = ""

    @Column(name = "value_type", length = 20, nullable = false)
    var valueType: String = "STRING"

    @Column(length = 500)
    var description: String? = null

    @Column(nullable = false)
    var version: Int = 1

    @Column(nullable = false)
    override var active: Boolean = true
}

@Repository
interface SystemConfigRepository : JpaRepository<SystemConfigEntity, Long> {
    fun findByDomainIdAndConfigKey(domainId: Long, configKey: String): SystemConfigEntity?
    fun findByConfigKey(configKey: String): SystemConfigEntity?
    fun findAllByActiveTrue(): List<SystemConfigEntity>
}
