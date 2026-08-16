package com.ntt.sysadmin.tenant.adapter.`in`.web.dto

/**
 * DTOs for Domain/Tenant Configuration API (FR-016).
 */

data class DomainConfigResponse(
    val domainId: Long,
    val branding: Any?,
    val loginPageConfig: Any?,
    val passwordPolicy: Any?,
    val mfaPolicy: Any?,
    val sessionPolicy: Any?,
    val allowedIpRanges: Any?,
    val maxUsers: Int,
    val maxApiPartners: Int
)

data class UpdateDomainConfigRequest(
    val brandingJson: String? = null,
    val loginPageConfigJson: String? = null,
    val passwordPolicyJson: String? = null,
    val mfaPolicyJson: String? = null,
    val sessionPolicyJson: String? = null,
    val allowedIpRangesJson: String? = null,
    val maxUsers: Int? = null,
    val maxApiPartners: Int? = null
)

data class BrandingResponse(
    val domainId: Long,
    val branding: Any?
)
