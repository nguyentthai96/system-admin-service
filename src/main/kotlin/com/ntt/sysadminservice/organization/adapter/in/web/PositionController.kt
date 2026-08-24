package com.ntt.sysadminservice.organization.adapter.`in`.web

import com.ntt.sysadminservice.organization.adapter.out.persistence.entity.PositionEntity
import com.ntt.sysadminservice.organization.adapter.out.persistence.entity.UserPositionEntity
import com.ntt.sysadminservice.organization.application.PositionService
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * Position controller — REST endpoints for position management (FR-011).
 */
@RestController
@RequestMapping("/api/admin/positions")
class PositionController(
    private val positionService: PositionService
) {

    @PostMapping
    fun createPosition(@Valid @RequestBody request: CreatePositionRequest): ResponseEntity<PositionEntity> {
        return ResponseEntity.ok(positionService.createPosition(request.departmentId, request.code, request.name, request.description))
    }

    @PutMapping("/{id}")
    fun updatePosition(@PathVariable id: Long, @Valid @RequestBody request: UpdatePositionRequest): ResponseEntity<PositionEntity> {
        return ResponseEntity.ok(positionService.updatePosition(id, request.name, request.description))
    }

    @DeleteMapping("/{id}")
    fun deletePosition(@PathVariable id: Long): ResponseEntity<Map<String, Boolean>> {
        positionService.deletePosition(id)
        return ResponseEntity.ok(mapOf("success" to true))
    }

    @PostMapping("/{id}/assign")
    fun assignUser(@PathVariable id: Long, @RequestBody request: AssignUserRequest): ResponseEntity<UserPositionEntity> {
        return ResponseEntity.ok(positionService.assignUser(id, request.userId, request.isPrimary))
    }
}

data class CreatePositionRequest(
    val departmentId: Long,
    @field:NotBlank val code: String,
    @field:NotBlank val name: String,
    val description: String? = null
)

data class UpdatePositionRequest(
    val name: String? = null,
    val description: String? = null
)

data class AssignUserRequest(
    val userId: Long,
    val isPrimary: Boolean = false
)
