package com.ntt.sysadminservice.organization.adapter.`in`.web

import com.ntt.sysadminservice.organization.adapter.out.persistence.entity.DepartmentEntity
import com.ntt.sysadminservice.organization.application.OrganizationService
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * Department controller — REST endpoints for organization tree (FR-011).
 */
@RestController
@RequestMapping("/api/admin/departments")
class DepartmentController(
    private val organizationService: OrganizationService
) {

    @GetMapping("/tree")
    fun getDepartmentTree(@RequestParam domainId: Long): ResponseEntity<List<DepartmentEntity>> {
        return ResponseEntity.ok(organizationService.getDepartmentTree(domainId))
    }

    @PostMapping
    fun createDepartment(@Valid @RequestBody request: CreateDepartmentRequest): ResponseEntity<DepartmentEntity> {
        val dept = organizationService.createDepartment(
            request.domainId, request.code, request.name, request.parentId, request.description
        )
        return ResponseEntity.ok(dept)
    }

    @PutMapping("/{id}")
    fun updateDepartment(@PathVariable id: Long, @Valid @RequestBody request: UpdateDepartmentRequest): ResponseEntity<DepartmentEntity> {
        return ResponseEntity.ok(organizationService.updateDepartment(id, request.name, request.description))
    }

    @PutMapping("/{id}/move")
    fun moveDepartment(@PathVariable id: Long, @RequestBody request: MoveDepartmentRequest): ResponseEntity<DepartmentEntity> {
        return ResponseEntity.ok(organizationService.moveDepartment(id, request.newParentId))
    }

    @DeleteMapping("/{id}")
    fun deleteDepartment(@PathVariable id: Long): ResponseEntity<Map<String, Boolean>> {
        organizationService.deleteDepartment(id)
        return ResponseEntity.ok(mapOf("success" to true))
    }

    /**
     * Get org chart with user counts and department heads (FR-011).
     */
    @GetMapping("/org-chart")
    fun getOrgChart(@RequestParam domainId: Long): ResponseEntity<List<Map<String, Any?>>> {
        return ResponseEntity.ok(organizationService.getOrgChart(domainId))
    }

    /**
     * Transfer a user between departments (FR-011).
     */
    @PostMapping("/transfer-user")
    fun transferUser(@Valid @RequestBody request: TransferUserRequest): ResponseEntity<Any> {
        val result = organizationService.transferUser(
            request.userId, request.fromDepartmentId, request.toDepartmentId, request.toPositionId
        )
        return ResponseEntity.ok(result)
    }

    /**
     * Assign a department head (FR-011).
     */
    @PostMapping("/{id}/head")
    fun assignDepartmentHead(
        @PathVariable id: Long,
        @RequestBody request: AssignHeadRequest
    ): ResponseEntity<DepartmentEntity> {
        return ResponseEntity.ok(organizationService.assignDepartmentHead(id, request.userId))
    }
}

data class TransferUserRequest(
    val userId: Long,
    val fromDepartmentId: Long,
    val toDepartmentId: Long,
    val toPositionId: Long
)

data class AssignHeadRequest(
    val userId: Long
)

data class CreateDepartmentRequest(
    val domainId: Long,
    @field:NotBlank val code: String,
    @field:NotBlank val name: String,
    val parentId: Long? = null,
    val description: String? = null
)

data class UpdateDepartmentRequest(
    val name: String? = null,
    val description: String? = null
)

data class MoveDepartmentRequest(
    val newParentId: Long? = null
)
