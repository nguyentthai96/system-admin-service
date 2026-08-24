package com.ntt.sysadminservice.config.application

import com.ntt.sysadminservice.config.adapter.out.persistence.entity.FeatureFlagEntity
import com.ntt.sysadminservice.shared.exception.SysAdminErrorCode
import com.ntt.sysadminservice.shared.exception.SysAdminException
import jakarta.persistence.EntityManager
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Feature flag service — boolean + percentage rollout + user segment targeting (FR-014).
 * Enhanced with segment-based targeting and consistent hash rollout.
 */
@Service
class FeatureFlagService(
    private val entityManager: EntityManager
) {

    private val log = LoggerFactory.getLogger(FeatureFlagService::class.java)

    /**
     * Get all feature flags for a domain.
     */
    fun getFlags(domainId: Long): List<FeatureFlagEntity> {
        return entityManager
            .createQuery("SELECT f FROM FeatureFlagEntity f WHERE f.domainId = :domainId AND f.active = true", FeatureFlagEntity::class.java)
            .setParameter("domainId", domainId)
            .resultList
    }

    /**
     * Check if a feature flag is enabled for a user (FR-014).
     * Resolution order:
     *   1. Flag globally disabled → false
     *   2. User in target segment → true
     *   3. Percentage rollout using consistent hashing
     */
    fun isEnabled(domainId: Long, flagKey: String, userId: Long? = null): Boolean {
        val flag = findByKey(domainId, flagKey) ?: return false
        if (!flag.enabled) return false

        // 100% rollout = enabled for all
        if (flag.rolloutPct >= 100) return true

        // Check user segment targeting
        if (userId != null && isUserInSegment(flag, userId)) {
            return true
        }

        // Percentage-based rollout using consistent hashing
        if (userId != null && flag.rolloutPct > 0) {
            val hash = consistentHash(userId, flagKey)
            return hash < flag.rolloutPct
        }

        return flag.rolloutPct >= 100
    }

    /**
     * Check if a feature flag is enabled for a user with segment context.
     */
    fun isEnabledForSegment(domainId: Long, flagKey: String, userId: Long, userSegmentIds: List<Long>): Boolean {
        val flag = findByKey(domainId, flagKey) ?: return false
        if (!flag.enabled) return false
        if (flag.rolloutPct >= 100) return true

        // Check if user's segments overlap with flag's target segments
        val targetSegments = parseSegments(flag.userSegments)
        if (targetSegments.isNotEmpty() && userSegmentIds.any { it in targetSegments }) {
            return true
        }

        // Fallback to percentage rollout
        if (flag.rolloutPct > 0) {
            val hash = consistentHash(userId, flagKey)
            return hash < flag.rolloutPct
        }

        return false
    }

    /**
     * Update a feature flag.
     */
    @Transactional
    fun updateFlag(domainId: Long, flagKey: String, enabled: Boolean?, rolloutPct: Int?, description: String?): FeatureFlagEntity {
        val flag = findByKey(domainId, flagKey)
            ?: throw SysAdminException(SysAdminErrorCode.CONFIG_NOT_FOUND, "Feature flag not found: $flagKey")

        enabled?.let { flag.enabled = it }
        rolloutPct?.let {
            if (it < 0 || it > 100) throw SysAdminException(SysAdminErrorCode.INVALID_CONFIG_TYPE, "Rollout percentage must be 0-100")
            flag.rolloutPct = it
        }
        description?.let { flag.description = it }

        log.info("Feature flag updated: domain={}, key={}, enabled={}, rollout={}%", domainId, flagKey, flag.enabled, flag.rolloutPct)
        return entityManager.merge(flag)
    }

    /**
     * Update target user segments for a feature flag (FR-014).
     */
    @Transactional
    fun updateFlagSegments(domainId: Long, flagKey: String, segmentIds: List<Long>): FeatureFlagEntity {
        val flag = findByKey(domainId, flagKey)
            ?: throw SysAdminException(SysAdminErrorCode.CONFIG_NOT_FOUND, "Feature flag not found: $flagKey")

        flag.userSegments = "[${segmentIds.joinToString(",")}]"

        log.info("Feature flag segments updated: domain={}, key={}, segments={}", domainId, flagKey, segmentIds)
        return entityManager.merge(flag)
    }

    /**
     * Create a new feature flag.
     */
    @Transactional
    fun createFlag(domainId: Long, flagKey: String, enabled: Boolean, rolloutPct: Int, description: String?): FeatureFlagEntity {
        val existing = findByKey(domainId, flagKey)
        if (existing != null) {
            throw SysAdminException(SysAdminErrorCode.GENERAL_ERROR, "Feature flag already exists: $flagKey")
        }

        val flag = FeatureFlagEntity().apply {
            this.domainId = domainId
            this.flagKey = flagKey
            this.enabled = enabled
            this.rolloutPct = rolloutPct
            this.description = description
        }
        entityManager.persist(flag)

        log.info("Feature flag created: domain={}, key={}, enabled={}, rollout={}%", domainId, flagKey, enabled, rolloutPct)
        return flag
    }

    private fun findByKey(domainId: Long, flagKey: String): FeatureFlagEntity? {
        return entityManager
            .createQuery("SELECT f FROM FeatureFlagEntity f WHERE f.domainId = :domainId AND f.flagKey = :key AND f.active = true", FeatureFlagEntity::class.java)
            .setParameter("domainId", domainId)
            .setParameter("key", flagKey)
            .resultList
            .firstOrNull()
    }

    /**
     * Check if a user is in the flag's target segments.
     */
    private fun isUserInSegment(flag: FeatureFlagEntity, userId: Long): Boolean {
        val targetSegments = parseSegments(flag.userSegments)
        if (targetSegments.isEmpty()) return false

        // Check if the user belongs to any of the target segments
        @Suppress("UNCHECKED_CAST")
        val userSegmentCount = try {
            entityManager
                .createNativeQuery("""
                    SELECT COUNT(*) FROM user_segments 
                    WHERE user_id = :userId AND segment_id IN (:segmentIds) AND active = true
                """)
                .setParameter("userId", userId)
                .setParameter("segmentIds", targetSegments)
                .singleResult as Number
        } catch (e: Exception) {
            return false // Table may not exist — graceful fallback
        }

        return userSegmentCount.toInt() > 0
    }

    private fun parseSegments(json: String): List<Long> {
        return try {
            json.trim().removeSurrounding("[", "]")
                .split(",")
                .filter { it.isNotBlank() }
                .map { it.trim().toLong() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Consistent hash for percentage rollout — deterministic per user+flag.
     */
    private fun consistentHash(userId: Long, flagKey: String): Int {
        val combined = "$userId:$flagKey"
        val hash = combined.hashCode() and Int.MAX_VALUE
        return hash % 100
    }
}
