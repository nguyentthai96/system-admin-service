package com.ntt.sysadmin.client.service

import com.github.benmanes.caffeine.cache.Caffeine
import com.ntt.basebusiness.shared.IConfigReader
import com.ntt.basebusiness.shared.IFeatureFlagReader
import com.ntt.basebusiness.shared.II18nReader
import com.ntt.basebusiness.shared.IMenuReader
import com.ntt.basebusiness.shared.vo.ConfigEntryVO
import com.ntt.basebusiness.shared.vo.FeatureFlagVO
import com.ntt.basebusiness.shared.vo.MenuItemVO
import com.ntt.sysadmin.client.repository.FeatureFlagReadRepository
import com.ntt.sysadmin.client.repository.I18nMessageReadRepository
import com.ntt.sysadmin.client.repository.MenuReadRepository
import com.ntt.sysadmin.client.repository.SystemConfigReadRepository
import java.text.MessageFormat
import java.time.Duration

/**
 * JPA-backed i18n reader with Caffeine L1 cache (TTL 10 min).
 * Cache key: "code:locale" → message string.
 */
class JpaI18nReader(
    private val repository: I18nMessageReadRepository
) : II18nReader {

    private val cache = Caffeine.newBuilder()
        .maximumSize(1000)
        .expireAfterWrite(Duration.ofMinutes(10))
        .build<String, String>()

    override fun getMessage(code: String, locale: String): String? {
        val cacheKey = "$code:$locale"
        return cache.get(cacheKey) {
            repository.findByCodeAndLocale(code, locale)?.message
        }
    }

    override fun getMessage(code: String, locale: String, args: Array<Any>): String? {
        val template = getMessage(code, locale) ?: return null
        return MessageFormat(template).format(args)
    }

    override fun getAllMessages(locale: String): Map<String, String> {
        return repository.findAllByLocaleAndIsActiveTrue(locale)
            .associate { it.code to it.message }
    }

    /** Invalidate all cached entries. Called on Redis Pub/Sub event. */
    fun invalidateAll() {
        cache.invalidateAll()
    }
}

/**
 * JPA-backed config reader with Caffeine L1 cache (TTL 10 min).
 */
class JpaConfigReader(
    private val repository: SystemConfigReadRepository
) : IConfigReader {

    private val cache = Caffeine.newBuilder()
        .maximumSize(500)
        .expireAfterWrite(Duration.ofMinutes(10))
        .build<String, ConfigEntryVO?>()

    override fun getConfig(key: String): ConfigEntryVO? {
        return cache.get(key) {
            repository.findByConfigKeyAndActiveTrue(key)?.let { entity ->
                ConfigEntryVO(
                    id = entity.id,
                    configKey = entity.configKey,
                    configValue = entity.configValue,
                    valueType = entity.valueType,
                    description = entity.description
                )
            }
        }
    }

    override fun getConfigValue(key: String): String? = getConfig(key)?.configValue

    override fun getConfigValue(key: String, defaultValue: String): String =
        getConfigValue(key) ?: defaultValue

    override fun getAllConfigs(): List<ConfigEntryVO> {
        return repository.findAllByActiveTrue().map { entity ->
            ConfigEntryVO(
                id = entity.id,
                configKey = entity.configKey,
                configValue = entity.configValue,
                valueType = entity.valueType,
                description = entity.description
            )
        }
    }

    fun invalidateAll() {
        cache.invalidateAll()
    }
}

/**
 * JPA-backed menu reader with Caffeine L1 cache (TTL 10 min).
 */
class JpaMenuReader(
    private val repository: MenuReadRepository
) : IMenuReader {

    private val allMenusCache = Caffeine.newBuilder()
        .maximumSize(1)
        .expireAfterWrite(Duration.ofMinutes(10))
        .build<String, List<MenuItemVO>>()

    override fun getAllMenuItems(): List<MenuItemVO> {
        return allMenusCache.get("ALL") {
            repository.findAllByStatusOrderBySortOrder("ACTIVE").map { it.toVO() }
        } ?: emptyList()
    }

    override fun getMenuItemsByParentId(parentId: Long?): List<MenuItemVO> {
        return repository.findByParentIdAndStatusOrderBySortOrder(parentId, "ACTIVE")
            .map { it.toVO() }
    }

    override fun getMenuItemByCode(code: String): MenuItemVO? {
        return repository.findByCodeAndStatus(code, "ACTIVE")?.toVO()
    }

    fun invalidateAll() {
        allMenusCache.invalidateAll()
    }

    private fun com.ntt.sysadmin.client.entity.MenuReadModel.toVO() = MenuItemVO(
        id = id,
        parentId = parentId,
        code = code,
        name = name,
        icon = icon,
        path = path,
        routeName = routeName,
        component = component,
        menuType = menuType,
        sortOrder = sortOrder,
        level = level,
        isVisible = isVisible,
        isCacheable = isCacheable,
        translateKey = translateKey,
        status = status
    )
}

/**
 * JPA-backed feature flag reader with Caffeine L1 cache (TTL 10 min).
 */
class JpaFeatureFlagReader(
    private val repository: FeatureFlagReadRepository
) : IFeatureFlagReader {

    private val cache = Caffeine.newBuilder()
        .maximumSize(200)
        .expireAfterWrite(Duration.ofMinutes(10))
        .build<String, FeatureFlagVO?>()

    override fun isEnabled(flagKey: String): Boolean {
        return getFlag(flagKey)?.enabled ?: false
    }

    override fun getFlag(flagKey: String): FeatureFlagVO? {
        return cache.get(flagKey) {
            repository.findByFlagKeyAndActiveTrue(flagKey)?.let { entity ->
                FeatureFlagVO(
                    id = entity.id,
                    flagKey = entity.flagKey,
                    enabled = entity.enabled,
                    rolloutPct = entity.rolloutPct,
                    description = entity.description
                )
            }
        }
    }

    override fun getAllFlags(): List<FeatureFlagVO> {
        return repository.findAllByActiveTrue().map { entity ->
            FeatureFlagVO(
                id = entity.id,
                flagKey = entity.flagKey,
                enabled = entity.enabled,
                rolloutPct = entity.rolloutPct,
                description = entity.description
            )
        }
    }

    fun invalidateAll() {
        cache.invalidateAll()
    }
}
