package com.ntt.sysadminservice.organization.application

import com.ntt.sysadminservice.organization.adapter.out.persistence.entity.PositionEntity
import com.ntt.sysadminservice.organization.adapter.out.persistence.entity.UserPositionEntity
import com.ntt.sysadminservice.shared.exception.SysAdminErrorCode
import com.ntt.sysadminservice.shared.exception.SysAdminException
import jakarta.persistence.EntityManager
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Position service — CRUD and user assignment (FR-011).
 */
@Service
class PositionService(
    private val entityManager: EntityManager
) {

    private val log = LoggerFactory.getLogger(PositionService::class.java)

    @Transactional
    fun createPosition(departmentId: Long, code: String, name: String, description: String?): PositionEntity {
        // Check duplicate code in department
        val existing = entityManager
            .createQuery("SELECT COUNT(p) FROM PositionEntity p WHERE p.departmentId = :deptId AND p.code = :code AND p.active = true", Long::class.javaObjectType)
            .setParameter("deptId", departmentId)
            .setParameter("code", code)
            .singleResult
        if (existing > 0) {
            throw SysAdminException(SysAdminErrorCode.POSITION_DUPLICATE, "Position code '$code' already exists in department $departmentId")
        }

        val position = PositionEntity().apply {
            this.departmentId = departmentId
            this.code = code
            this.name = name
            this.description = description
        }
        entityManager.persist(position)
        log.info("Position created: id={}, code={}, department={}", position.id, code, departmentId)
        return position
    }

    @Transactional
    fun updatePosition(id: Long, name: String?, description: String?): PositionEntity {
        val position = entityManager.find(PositionEntity::class.java, id)?.takeIf { it.active }
            ?: throw SysAdminException(SysAdminErrorCode.NOT_FOUND, "Position not found: $id")

        name?.let { position.name = it }
        description?.let { position.description = it }

        return entityManager.merge(position)
    }

    @Transactional
    fun deletePosition(id: Long) {
        val position = entityManager.find(PositionEntity::class.java, id)?.takeIf { it.active }
            ?: throw SysAdminException(SysAdminErrorCode.NOT_FOUND, "Position not found: $id")

        position.active = false
        entityManager.merge(position)
        log.info("Position deleted: id={}", id)
    }

    @Transactional
    fun assignUser(positionId: Long, userId: Long, isPrimary: Boolean = false): UserPositionEntity {
        val position = entityManager.find(PositionEntity::class.java, positionId)?.takeIf { it.active }
            ?: throw SysAdminException(SysAdminErrorCode.NOT_FOUND, "Position not found: $positionId")

        val existing = entityManager
            .createQuery("SELECT up FROM UserPositionEntity up WHERE up.userId = :userId AND up.positionId = :posId AND up.active = true", UserPositionEntity::class.java)
            .setParameter("userId", userId)
            .setParameter("posId", positionId)
            .resultList
            .firstOrNull()

        if (existing != null) {
            log.warn("User {} already assigned to position {}", userId, positionId)
            return existing
        }

        val assignment = UserPositionEntity().apply {
            this.userId = userId
            this.positionId = positionId
            this.isPrimary = isPrimary
        }
        entityManager.persist(assignment)
        log.info("User {} assigned to position {}", userId, positionId)
        return assignment
    }
}
