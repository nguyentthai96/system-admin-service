package com.ntt.sysadminservice.audit.application

import com.ntt.sysadminservice.audit.adapter.out.persistence.entity.AuditLogEntity
import jakarta.persistence.EntityManager
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import javax.sql.DataSource

/**
 * Immutable audit service (FR-015).
 * Uses direct JDBC for inserts to guarantee immutability (bypass JPA update).
 * Supports search, filtering, and export.
 */
@Service
class AuditService(
    private val entityManager: EntityManager,
    private val dataSource: DataSource
) {

    private val log = LoggerFactory.getLogger(AuditService::class.java)

    /**
     * Record an audit event — immutable insert via direct JDBC.
     */
    @Transactional
    fun recordAudit(
        userId: Long?,
        action: String,
        entityType: String?,
        entityId: String?,
        oldValue: String?,
        newValue: String?,
        ipAddress: String?,
        userAgent: String?,
        details: String?
    ) {
        val sql = """
            INSERT INTO audit_logs (id, user_id, action, entity_type, entity_id, old_value, new_value, 
                                    ip_address, user_agent, details, occurred_at, active, created_at)
            VALUES (nextval('snowflake_seq'), ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, NOW(), true, NOW())
        """.trimIndent()

        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setObject(1, userId)
                stmt.setString(2, action)
                stmt.setString(3, entityType)
                stmt.setString(4, entityId)
                stmt.setString(5, oldValue)
                stmt.setString(6, newValue)
                stmt.setString(7, ipAddress)
                stmt.setString(8, userAgent)
                stmt.setString(9, details)
                stmt.executeUpdate()
            }
        }

        log.info("Audit recorded: action={}, entityType={}, entityId={}, userId={}", action, entityType, entityId, userId)
    }

    /**
     * Search audit logs with pagination and filtering.
     */
    fun searchAuditLogs(
        userId: Long?,
        action: String?,
        entityType: String?,
        from: Instant?,
        to: Instant?,
        pageable: Pageable
    ): Page<AuditLogEntity> {
        val jpql = buildString {
            append("SELECT a FROM AuditLogEntity a WHERE 1=1")
            if (userId != null) append(" AND a.userId = :userId")
            if (action != null) append(" AND a.action = :action")
            if (entityType != null) append(" AND a.entityType = :entityType")
            if (from != null) append(" AND a.occurredAt >= :from")
            if (to != null) append(" AND a.occurredAt <= :to")
            append(" ORDER BY a.occurredAt DESC")
        }

        val query = entityManager.createQuery(jpql, AuditLogEntity::class.java)
        if (userId != null) query.setParameter("userId", userId)
        if (action != null) query.setParameter("action", action)
        if (entityType != null) query.setParameter("entityType", entityType)
        if (from != null) query.setParameter("from", from)
        if (to != null) query.setParameter("to", to)

        query.firstResult = pageable.offset.toInt()
        query.maxResults = pageable.pageSize

        val results = query.resultList

        // Count query
        val countJpql = jpql.replace("SELECT a FROM", "SELECT COUNT(a) FROM").substringBefore("ORDER BY")
        val countQuery = entityManager.createQuery(countJpql, Long::class.javaObjectType)
        if (userId != null) countQuery.setParameter("userId", userId)
        if (action != null) countQuery.setParameter("action", action)
        if (entityType != null) countQuery.setParameter("entityType", entityType)
        if (from != null) countQuery.setParameter("from", from)
        if (to != null) countQuery.setParameter("to", to)

        val total = countQuery.singleResult

        return PageImpl(results, pageable, total)
    }

    /**
     * Export audit logs as list (for CSV/JSON export).
     */
    fun exportAuditLogs(from: Instant?, to: Instant?, limit: Int = 10000): List<AuditLogEntity> {
        val jpql = buildString {
            append("SELECT a FROM AuditLogEntity a WHERE 1=1")
            if (from != null) append(" AND a.occurredAt >= :from")
            if (to != null) append(" AND a.occurredAt <= :to")
            append(" ORDER BY a.occurredAt DESC")
        }

        val query = entityManager.createQuery(jpql, AuditLogEntity::class.java)
        if (from != null) query.setParameter("from", from)
        if (to != null) query.setParameter("to", to)
        query.maxResults = limit

        return query.resultList
    }
}
