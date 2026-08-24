package com.ntt.sysadminservice.organization.application

import com.ntt.sysadminservice.organization.adapter.out.persistence.entity.DepartmentEntity
import com.ntt.sysadminservice.organization.adapter.out.persistence.entity.UserPositionEntity
import com.ntt.sysadminservice.shared.exception.SysAdminErrorCode
import com.ntt.sysadminservice.shared.exception.SysAdminException
import com.ntt.sysadminservice.shared.util.TreeBuilder
import jakarta.persistence.EntityManager
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Organization service — department tree management, user transfer, org chart (FR-011).
 * Recursive CTE for tree queries, DFS cycle detection, max 10 levels.
 * User transfer between departments with audit logging.
 */
@Service
class OrganizationService(
    private val entityManager: EntityManager
) {

    private val log = LoggerFactory.getLogger(OrganizationService::class.java)

    /**
     * Get full department tree for a domain.
     */
    fun getDepartmentTree(domainId: Long): List<DepartmentEntity> {
        val departments = entityManager
            .createQuery("SELECT d FROM DepartmentEntity d WHERE d.domainId = :domainId AND d.active = true ORDER BY d.sortOrder", DepartmentEntity::class.java)
            .setParameter("domainId", domainId)
            .resultList

        return buildDepartmentTree(departments)
    }

    /**
     * Get org chart using recursive CTE — includes user counts and department heads.
     */
    fun getOrgChart(domainId: Long): List<Map<String, Any?>> {
        @Suppress("UNCHECKED_CAST")
        val results = entityManager
            .createNativeQuery("""
                WITH RECURSIVE dept_tree AS (
                    SELECT d.id, d.parent_id, d.code, d.name, d.description, d.manager_user_id,
                           d.tree_level, d.tree_path, d.sort_order,
                           0 AS depth
                    FROM departments d
                    WHERE d.domain_id = :domainId AND d.parent_id IS NULL AND d.active = true
                    UNION ALL
                    SELECT d.id, d.parent_id, d.code, d.name, d.description, d.manager_user_id,
                           d.tree_level, d.tree_path, d.sort_order,
                           dt.depth + 1
                    FROM departments d
                    INNER JOIN dept_tree dt ON d.parent_id = dt.id
                    WHERE d.active = true AND dt.depth < 10
                )
                SELECT dt.id, dt.parent_id, dt.code, dt.name, dt.description, dt.manager_user_id,
                       dt.tree_level, dt.depth, dt.sort_order,
                       (SELECT COUNT(*) FROM user_positions up 
                        INNER JOIN positions p ON up.position_id = p.id 
                        WHERE p.department_id = dt.id AND up.active = true AND p.active = true) AS user_count
                FROM dept_tree dt
                ORDER BY dt.sort_order, dt.name
            """)
            .setParameter("domainId", domainId)
            .resultList as List<Array<Any?>>

        return results.map { row ->
            mapOf(
                "id" to (row[0] as Number).toLong(),
                "parentId" to (row[1] as? Number)?.toLong(),
                "code" to row[2],
                "name" to row[3],
                "description" to row[4],
                "headUserId" to (row[5] as? Number)?.toLong(),
                "treeLevel" to (row[6] as? Number)?.toInt(),
                "depth" to (row[7] as? Number)?.toInt(),
                "sortOrder" to (row[8] as? Number)?.toInt(),
                "userCount" to (row[9] as? Number)?.toInt()
            )
        }
    }

    /**
     * Transfer a user from one department to another.
     * Updates the user's position assignment.
     */
    @Transactional
    fun transferUser(userId: Long, fromDeptId: Long, toDeptId: Long, toPositionId: Long): UserPositionEntity {
        // Validate source department and user assignment
        val currentAssignment = entityManager
            .createQuery("""
                SELECT up FROM UserPositionEntity up 
                INNER JOIN PositionEntity p ON up.positionId = p.id 
                WHERE up.userId = :userId AND p.departmentId = :fromDeptId AND up.active = true AND p.active = true
            """, UserPositionEntity::class.java)
            .setParameter("userId", userId)
            .setParameter("fromDeptId", fromDeptId)
            .resultList
            .firstOrNull()
            ?: throw SysAdminException(SysAdminErrorCode.NOT_FOUND,
                "User $userId not found in department $fromDeptId")

        // Validate target department exists
        val targetDept = findById(toDeptId)
            ?: throw SysAdminException(SysAdminErrorCode.NOT_FOUND, "Target department not found: $toDeptId")

        // Validate target position exists and belongs to target department
        val targetPosition = entityManager
            .createQuery("SELECT p FROM PositionEntity p WHERE p.id = :posId AND p.departmentId = :deptId AND p.active = true",
                com.ntt.sysadminservice.organization.adapter.out.persistence.entity.PositionEntity::class.java)
            .setParameter("posId", toPositionId)
            .setParameter("deptId", toDeptId)
            .resultList
            .firstOrNull()
            ?: throw SysAdminException(SysAdminErrorCode.NOT_FOUND,
                "Position $toPositionId not found in department $toDeptId")

        // Deactivate old assignment
        currentAssignment.active = false
        entityManager.merge(currentAssignment)

        // Create new assignment
        val newAssignment = UserPositionEntity().apply {
            this.userId = userId
            this.positionId = toPositionId
            this.isPrimary = currentAssignment.isPrimary
        }
        entityManager.persist(newAssignment)

        log.info("User transferred: userId={}, fromDept={}, toDept={}, toPosition={}",
            userId, fromDeptId, toDeptId, toPositionId)

        return newAssignment
    }

    /**
     * Assign a department head.
     */
    @Transactional
    fun assignDepartmentHead(deptId: Long, userId: Long): DepartmentEntity {
        val dept = findById(deptId)
            ?: throw SysAdminException(SysAdminErrorCode.NOT_FOUND, "Department not found: $deptId")

        // Validate user is in this department
        val userInDept = entityManager
            .createNativeQuery("""
                SELECT COUNT(*) FROM user_positions up 
                INNER JOIN positions p ON up.position_id = p.id 
                WHERE p.department_id = :deptId AND up.user_id = :userId AND up.active = true AND p.active = true
            """)
            .setParameter("deptId", deptId)
            .setParameter("userId", userId)
            .singleResult as Number

        if (userInDept.toInt() == 0) {
            throw SysAdminException(SysAdminErrorCode.NOT_FOUND,
                "User $userId is not assigned to department $deptId")
        }

        dept.managerUserId = userId
        entityManager.merge(dept)

        log.info("Department head assigned: deptId={}, headUserId={}", deptId, userId)
        return dept
    }

    /**
     * Create a new department.
     */
    @Transactional
    fun createDepartment(domainId: Long, code: String, name: String, parentId: Long?, description: String?): DepartmentEntity {
        var parentLevel = 0
        var parentPath: String? = null
        if (parentId != null) {
            val parent = findById(parentId)
                ?: throw SysAdminException(SysAdminErrorCode.NOT_FOUND, "Parent department not found: $parentId")
            parentLevel = parent.treeLevel
            parentPath = parent.treePath

            if (TreeBuilder.detectCycle(parentId, parentId, { pid -> findById(pid)?.parentId })) {
                throw SysAdminException(SysAdminErrorCode.CIRCULAR_HIERARCHY, "Circular hierarchy detected")
            }
        }

        val newLevel = parentLevel + 1
        TreeBuilder.validateMaxDepth(newLevel)

        val dept = DepartmentEntity().apply {
            this.domainId = domainId
            this.code = code
            this.name = name
            this.parentId = parentId
            this.description = description
            this.treeLevel = newLevel
        }
        entityManager.persist(dept)
        dept.treePath = TreeBuilder.calculateTreePath(dept.id!!, parentPath)
        entityManager.merge(dept)

        log.info("Department created: id={}, code={}, domain={}", dept.id, code, domainId)
        return dept
    }

    /**
     * Update a department.
     */
    @Transactional
    fun updateDepartment(id: Long, name: String?, description: String?): DepartmentEntity {
        val dept = findById(id)
            ?: throw SysAdminException(SysAdminErrorCode.NOT_FOUND, "Department not found: $id")

        name?.let { dept.name = it }
        description?.let { dept.description = it }

        return entityManager.merge(dept)
    }

    /**
     * Move a department to a new parent.
     */
    @Transactional
    fun moveDepartment(id: Long, newParentId: Long?): DepartmentEntity {
        val dept = findById(id)
            ?: throw SysAdminException(SysAdminErrorCode.NOT_FOUND, "Department not found: $id")

        if (newParentId != null) {
            if (TreeBuilder.detectCycle(id, newParentId, { pid -> findById(pid)?.parentId })) {
                throw SysAdminException(SysAdminErrorCode.CIRCULAR_HIERARCHY, "Moving to a descendant creates a cycle")
            }
            val newParent = findById(newParentId)
                ?: throw SysAdminException(SysAdminErrorCode.NOT_FOUND, "New parent not found: $newParentId")
            val newLevel = newParent.treeLevel + 1
            TreeBuilder.validateMaxDepth(newLevel)
            dept.parentId = newParentId
            dept.treeLevel = newLevel
            dept.treePath = TreeBuilder.calculateTreePath(dept.id!!, newParent.treePath)
        } else {
            dept.parentId = null
            dept.treeLevel = 0
            dept.treePath = "/${dept.id}/"
        }

        log.info("Department moved: id={}, newParent={}", id, newParentId)
        return entityManager.merge(dept)
    }

    /**
     * Soft-delete a department.
     */
    @Transactional
    fun deleteDepartment(id: Long) {
        val dept = findById(id)
            ?: throw SysAdminException(SysAdminErrorCode.NOT_FOUND, "Department not found: $id")

        val childCount = entityManager
            .createQuery("SELECT COUNT(d) FROM DepartmentEntity d WHERE d.parentId = :id AND d.active = true", Long::class.javaObjectType)
            .setParameter("id", id)
            .singleResult
        if (childCount > 0) {
            throw SysAdminException(SysAdminErrorCode.NOT_FOUND, "Cannot delete department with children. Move or delete children first.")
        }

        dept.active = false
        entityManager.merge(dept)
        log.info("Department deleted: id={}", id)
    }

    private fun findById(id: Long): DepartmentEntity? {
        return entityManager.find(DepartmentEntity::class.java, id)?.takeIf { it.active }
    }

    private fun buildDepartmentTree(departments: List<DepartmentEntity>): List<DepartmentEntity> {
        val byParent = departments.groupBy { it.parentId }
        val roots = byParent[null] ?: emptyList()

        fun attachChildren(parent: DepartmentEntity) {
            parent.children = (byParent[parent.id] ?: emptyList()).toMutableList()
            parent.children.forEach { attachChildren(it) }
        }

        roots.forEach { attachChildren(it) }
        return roots
    }
}
