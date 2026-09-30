package com.ntt.sysadmin.versioning.domain

import com.ntt.basecore.domain.file.ExportTemplate
import org.springframework.stereotype.Component

/**
 * Unified SPI for all versioned configuration domains (FR-001, FR-009, FR-010, FR-014).
 *
 * Provides a generic contract for point-in-time snapshotting, state diffs,
 * file export templates, and conflict-aware rollbacks.
 *
 * @param E Entity type representing the domain aggregate
 */
interface VersionedConfigDomain<E : Any> {

    /**
     * Unique domain identifier (e.g., "SYSTEM_CONFIG", "I18N_MESSAGE", "MENU_ITEM", "DOMAIN_CONFIG").
     */
    val domainName: String

    /**
     * Entity class reference for Jackson serialization / deserialization.
     */
    val entityClass: Class<E>

    /**
     * Extracts a natural business key for conflict detection and diff matching (e.g. configKey, code+locale, code).
     */
    val naturalKeyExtractor: (E) -> String

    /**
     * Extracts entity last updated timestamp (epoch millis) for rollback conflict detection.
     * Returns null if entity does not track timestamps.
     */
    fun getEntityUpdatedAt(entity: E): Long? = null

    /**
     * Fetches the current live state of all records in this configuration domain.
     */
    fun fetchCurrentState(): List<E>

    /**
     * Applies the rollback state deserialized from a historical snapshot payload.
     *
     * @param snapshotPayload JSON string containing the list of entity records from the snapshot
     * @param forceOverwrite If true, ignores conflict warnings and overwrites newer modifications
     */
    fun applyRollbackState(snapshotPayload: String, forceOverwrite: Boolean = false)

    /**
     * Returns the export template for exporting this domain via base-file-starter.
     */
    fun exportTemplate(): ExportTemplate<E>? = null
}

/**
 * Central registry discovering and indexing all VersionedConfigDomain beans in the application context.
 */
@Component
class ConfigDomainRegistry(
    private val domains: List<VersionedConfigDomain<*>>
) {
    private val domainMap: Map<String, VersionedConfigDomain<*>> = domains.associateBy { it.domainName.uppercase() }

    fun getDomain(domainName: String): VersionedConfigDomain<*>? {
        return domainMap[domainName.uppercase()]
    }

    @Suppress("UNCHECKED_CAST")
    fun <E : Any> getTypedDomain(domainName: String): VersionedConfigDomain<E>? {
        return domainMap[domainName.uppercase()] as? VersionedConfigDomain<E>
    }

    fun getAllDomains(): List<VersionedConfigDomain<*>> = domains

    fun getRegisteredDomainNames(): Set<String> = domainMap.keys
}
