package com.ntt.sysadminservice.config

import com.ntt.basecore.autoconfigure.file.imports.DefaultSimpleImportHandler
import com.ntt.basecore.autoconfigure.file.imports.ImportContext
import com.ntt.sysadminservice.config.adapter.out.persistence.entity.FeatureFlagEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component

/**
 * Repository for FeatureFlagEntity.
 */
interface FeatureFlagRepository : JpaRepository<FeatureFlagEntity, Long>

/**
 * Import handler for feature flag records.
 * Supports all 4 import modes via DefaultSimpleImportHandler.
 * Invalidates Redis feature flag cache after import.
 */
@Component
class FeatureFlagImportHandler(
    featureFlagRepository: FeatureFlagRepository,
    private val redisTemplate: StringRedisTemplate
) : DefaultSimpleImportHandler<FeatureFlagEntity, Long>(
    tableName = "feature_flags",
    repository = featureFlagRepository,
    order = 5
) {

    override fun cleanup(context: ImportContext) {
        // Invalidate feature flag cache after import
        val keys = redisTemplate.keys("feature_flag:*")
        if (!keys.isNullOrEmpty()) {
            redisTemplate.delete(keys)
        }
    }
}
