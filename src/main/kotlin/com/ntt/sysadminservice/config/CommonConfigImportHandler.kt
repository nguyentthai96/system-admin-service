package com.ntt.sysadminservice.config

import com.ntt.basecore.autoconfigure.file.imports.DefaultSimpleImportHandler
import com.ntt.sysadmin.versioning.domain.entity.SystemConfigEntity
import com.ntt.sysadmin.versioning.domain.entity.SystemConfigRepository
import org.springframework.stereotype.Component

/**
 * Import handler for flat system configuration records.
 * Supports all 4 import modes via DefaultSimpleImportHandler.
 *
 * Natural key: config_key (unique per domain).
 */
@Component
class CommonConfigImportHandler(
    systemConfigRepository: SystemConfigRepository
) : DefaultSimpleImportHandler<SystemConfigEntity, Long>(
    tableName = "system_configs",
    repository = systemConfigRepository,
    order = 0
)
