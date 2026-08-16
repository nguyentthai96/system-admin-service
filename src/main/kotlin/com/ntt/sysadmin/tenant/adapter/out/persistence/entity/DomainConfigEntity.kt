package com.ntt.sysadmin.tenant.adapter.out.persistence.entity

import com.ntt.basecore.model.id.SnowflakePersistentAuditableEntity
import jakarta.persistence.*

/**
 * Per-domain tenant configuration (FR-016).
 * Stores branding, security policies, and limits per tenant.
 */
@Entity
@Table(name = "domain_configs")
class DomainConfigEntity : SnowflakePersistentAuditableEntity() {

    @Column(name = "domain_id", nullable = false, unique = true)
    var domainId: Long = 0

    @Column(name = "branding_json", columnDefinition = "TEXT")
    var brandingJson: String? = null

    @Column(name = "login_page_config_json", columnDefinition = "TEXT")
    var loginPageConfigJson: String? = null

    @Column(name = "password_policy_json", columnDefinition = "TEXT")
    var passwordPolicyJson: String? = null

    @Column(name = "mfa_policy_json", columnDefinition = "TEXT")
    var mfaPolicyJson: String? = null

    @Column(name = "session_policy_json", columnDefinition = "TEXT")
    var sessionPolicyJson: String? = null

    @Column(name = "allowed_ip_ranges_json", columnDefinition = "TEXT")
    var allowedIpRangesJson: String? = null

    @Column(name = "max_users", nullable = false)
    var maxUsers: Int = 1000

    @Column(name = "max_api_partners", nullable = false)
    var maxApiPartners: Int = 50
}
