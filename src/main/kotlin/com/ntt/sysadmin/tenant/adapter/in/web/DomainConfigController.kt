package com.ntt.sysadmin.tenant.adapter.`in`.web

import com.ntt.sysadmin.tenant.adapter.`in`.web.dto.*
import com.ntt.sysadmin.tenant.application.DomainConfigService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * REST Controller for Domain/Tenant Configuration (FR-016).
 */
@RestController
@RequestMapping("/api/admin/domains")
class DomainConfigController(
    private val domainConfigService: DomainConfigService
) {

    /**
     * Get domain configuration.
     */
    @GetMapping("/{domainId}/config")
    fun getDomainConfig(
        @PathVariable domainId: Long
    ): ResponseEntity<DomainConfigResponse> {
        return ResponseEntity.ok(domainConfigService.getDomainConfig(domainId))
    }

    /**
     * Update domain configuration.
     */
    @PutMapping("/{domainId}/config")
    fun updateDomainConfig(
        @PathVariable domainId: Long,
        @RequestBody request: UpdateDomainConfigRequest
    ): ResponseEntity<DomainConfigResponse> {
        return ResponseEntity.ok(domainConfigService.updateDomainConfig(domainId, request))
    }

    /**
     * Get branding config for a domain (public endpoint for login page customization).
     */
    @GetMapping("/{domainId}/branding")
    fun getBranding(
        @PathVariable domainId: Long
    ): ResponseEntity<BrandingResponse> {
        val branding = domainConfigService.getBranding(domainId)
        return ResponseEntity.ok(BrandingResponse(domainId = domainId, branding = branding))
    }
}
