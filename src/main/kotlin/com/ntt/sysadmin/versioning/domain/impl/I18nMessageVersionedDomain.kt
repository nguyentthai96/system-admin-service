package com.ntt.sysadmin.versioning.domain.impl

import com.fasterxml.jackson.databind.ObjectMapper
import com.ntt.basecore.domain.file.ColumnDefinition
import com.ntt.basecore.domain.file.ExportTemplate
import com.ntt.sysadmin.versioning.domain.VersionedConfigDomain
import com.ntt.sysadmin.versioning.domain.entity.I18nMessageEntity
import com.ntt.sysadmin.versioning.domain.entity.I18nMessageRepository
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Versioned configuration domain implementation for multilingual i18n messages (FR-001, FR-006).
 * Handles natural key matching (code:locale), database persistence, and Redis cache invalidation.
 */
@Component
class I18nMessageVersionedDomain(
    private val repository: I18nMessageRepository,
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper
) : VersionedConfigDomain<I18nMessageEntity> {

    private val log = LoggerFactory.getLogger(javaClass)

    override val domainName: String = "I18N_MESSAGE"

    override val entityClass: Class<I18nMessageEntity> = I18nMessageEntity::class.java

    override val naturalKeyExtractor: (I18nMessageEntity) -> String = { "${it.code}:${it.locale}" }

    override fun getEntityUpdatedAt(entity: I18nMessageEntity): Long = entity.updatedAt

    override fun fetchCurrentState(): List<I18nMessageEntity> {
        return repository.findAll()
    }

    @Transactional
    override fun applyRollbackState(snapshotPayload: String, forceOverwrite: Boolean) {
        val listType = objectMapper.typeFactory.constructCollectionType(List::class.java, I18nMessageEntity::class.java)
        val snapshotEntities: List<I18nMessageEntity> = objectMapper.readValue(snapshotPayload, listType)

        log.info("Applying rollback for I18N_MESSAGE: {} records to restore", snapshotEntities.size)

        for (snapshotItem in snapshotEntities) {
            val existing = repository.findByCodeAndLocale(snapshotItem.code, snapshotItem.locale)
            val now = System.currentTimeMillis()
            if (existing != null) {
                existing.message = snapshotItem.message
                existing.module = snapshotItem.module
                existing.isActive = snapshotItem.isActive
                existing.updatedAt = now
                repository.save(existing)
            } else {
                val newEntity = I18nMessageEntity().apply {
                    this.code = snapshotItem.code
                    this.locale = snapshotItem.locale
                    this.message = snapshotItem.message
                    this.module = snapshotItem.module
                    this.isActive = snapshotItem.isActive
                    this.createdAt = now
                    this.updatedAt = now
                }
                repository.save(newEntity)
            }

            // Invalidate Redis cache for updated message
            try {
                redisTemplate.delete("i18n:${snapshotItem.locale}:${snapshotItem.code}")
            } catch (ex: Exception) {
                log.warn("Redis eviction failed for key {}:{}: {}", snapshotItem.code, snapshotItem.locale, ex.message)
            }
        }
    }

    override fun exportTemplate(): ExportTemplate<I18nMessageEntity> {
        return object : ExportTemplate<I18nMessageEntity> {
            override val templateId: String = "i18n-message-export"
            override val columns: List<ColumnDefinition> = listOf(
                ColumnDefinition("Code", "code"),
                ColumnDefinition("Locale", "locale"),
                ColumnDefinition("Message", "message"),
                ColumnDefinition("Module", "module"),
                ColumnDefinition("Active", "isActive")
            )
            override fun dataQuery(filter: Map<String, Any>?): java.util.stream.Stream<I18nMessageEntity> {
                return repository.findAll().stream()
            }
        }
    }
}
