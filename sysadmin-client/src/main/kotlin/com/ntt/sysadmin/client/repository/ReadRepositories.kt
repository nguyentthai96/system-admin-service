package com.ntt.sysadmin.client.repository

import com.ntt.sysadmin.client.entity.FeatureFlagReadModel
import com.ntt.sysadmin.client.entity.I18nMessageReadModel
import com.ntt.sysadmin.client.entity.MenuReadModel
import com.ntt.sysadmin.client.entity.SystemConfigReadModel
import org.springframework.data.repository.Repository

/**
 * Read-only repository for i18n messages.
 * Extends marker Repository interface — no save/delete methods exposed.
 */
interface I18nMessageReadRepository : Repository<I18nMessageReadModel, Long> {
    fun findByCodeAndLocale(code: String, locale: String): I18nMessageReadModel?
    fun findAllByLocaleAndIsActiveTrue(locale: String): List<I18nMessageReadModel>
    fun findAllByIsActiveTrue(): List<I18nMessageReadModel>
}

/**
 * Read-only repository for system configurations.
 */
interface SystemConfigReadRepository : Repository<SystemConfigReadModel, Long> {
    fun findByConfigKeyAndActiveTrue(configKey: String): SystemConfigReadModel?
    fun findAllByActiveTrue(): List<SystemConfigReadModel>
}

/**
 * Read-only repository for menu items.
 */
interface MenuReadRepository : Repository<MenuReadModel, Long> {
    fun findAllByStatusOrderBySortOrder(status: String = "ACTIVE"): List<MenuReadModel>
    fun findByParentIdAndStatusOrderBySortOrder(parentId: Long?, status: String = "ACTIVE"): List<MenuReadModel>
    fun findByCodeAndStatus(code: String, status: String = "ACTIVE"): MenuReadModel?
}

/**
 * Read-only repository for feature flags.
 */
interface FeatureFlagReadRepository : Repository<FeatureFlagReadModel, Long> {
    fun findByFlagKeyAndActiveTrue(flagKey: String): FeatureFlagReadModel?
    fun findAllByActiveTrue(): List<FeatureFlagReadModel>
}
