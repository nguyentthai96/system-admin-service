package com.ntt.sysadmin.versioning.domain.impl

import com.fasterxml.jackson.databind.ObjectMapper
import com.ntt.basecore.domain.file.ColumnDefinition
import com.ntt.basecore.domain.file.ExportTemplate
import com.ntt.sysadmin.versioning.domain.VersionedConfigDomain
import com.ntt.sysadmin.versioning.domain.entity.SystemConfigEntity
import com.ntt.sysadmin.versioning.domain.entity.SystemConfigRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Versioned configuration domain implementation for dynamic system parameters (FR-001, FR-006).
 */
@Component
class SystemConfigVersionedDomain(
    private val repository: SystemConfigRepository,
    private val objectMapper: ObjectMapper
) : VersionedConfigDomain<SystemConfigEntity> {

    private val log = LoggerFactory.getLogger(javaClass)

    override val domainName: String = "SYSTEM_CONFIG"

    override val entityClass: Class<SystemConfigEntity> = SystemConfigEntity::class.java

    override val naturalKeyExtractor: (SystemConfigEntity) -> String = { it.configKey }

    override fun getEntityUpdatedAt(entity: SystemConfigEntity): Long? = entity.updatedAt?.toEpochMilli()

    override fun fetchCurrentState(): List<SystemConfigEntity> {
        return repository.findAll()
    }

    @Transactional
    override fun applyRollbackState(snapshotPayload: String, forceOverwrite: Boolean) {
        val listType = objectMapper.typeFactory.constructCollectionType(List::class.java, SystemConfigEntity::class.java)
        val snapshotEntities: List<SystemConfigEntity> = objectMapper.readValue(snapshotPayload, listType)

        log.info("Applying rollback for SYSTEM_CONFIG: {} records to restore", snapshotEntities.size)

        for (snapshotItem in snapshotEntities) {
            val existing = repository.findByConfigKey(snapshotItem.configKey)
            if (existing != null) {
                existing.configValue = snapshotItem.configValue
                existing.valueType = snapshotItem.valueType
                existing.description = snapshotItem.description
                existing.active = snapshotItem.active
                existing.version = existing.version + 1
                repository.save(existing)
            } else {
                val newEntity = SystemConfigEntity().apply {
                    this.domainId = snapshotItem.domainId
                    this.configKey = snapshotItem.configKey
                    this.configValue = snapshotItem.configValue
                    this.valueType = snapshotItem.valueType
                    this.description = snapshotItem.description
                    this.version = 1
                    this.active = snapshotItem.active
                }
                repository.save(newEntity)
            }
        }
    }

    override fun exportTemplate(): ExportTemplate<SystemConfigEntity> {
        return object : ExportTemplate<SystemConfigEntity> {
            override val templateId: String = "system-config-export"
            override val columns: List<ColumnDefinition> = listOf(
                ColumnDefinition("Config Key", "configKey"),
                ColumnDefinition("Config Value", "configValue"),
                ColumnDefinition("Type", "valueType"),
                ColumnDefinition("Description", "description"),
                ColumnDefinition("Active", "active")
            )
            override fun dataQuery(filter: Map<String, Any>?): java.util.stream.Stream<SystemConfigEntity> {
                return repository.findAll().stream()
            }
        }
    }
}
