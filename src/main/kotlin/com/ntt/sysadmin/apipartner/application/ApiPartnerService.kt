package com.ntt.sysadmin.apipartner.application

import com.ntt.sysadmin.apipartner.adapter.`in`.web.dto.PartnerResponse
import com.ntt.sysadmin.apipartner.adapter.`in`.web.dto.RegisterPartnerRequest
import com.ntt.sysadmin.apipartner.adapter.out.persistence.entity.ApiPartnerEntity
import com.ntt.sysadmin.apipartner.adapter.out.persistence.repository.ApiPartnerRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * API Partner management service (FR-012).
 */
@Service
class ApiPartnerService(
    private val partnerRepository: ApiPartnerRepository
) {

    private val log = LoggerFactory.getLogger(ApiPartnerService::class.java)

    @Transactional
    fun registerPartner(request: RegisterPartnerRequest): ApiPartnerEntity {
        // Check duplicate partner code
        val existing = partnerRepository.findByPartnerCode(request.partnerCode)
        if (existing != null) {
            throw IllegalArgumentException("Partner code already exists: ${request.partnerCode}")
        }

        val entity = ApiPartnerEntity().apply {
            domainId = request.domainId
            partnerName = request.partnerName
            partnerCode = request.partnerCode
            contactEmail = request.contactEmail
            contactPhone = request.contactPhone
            description = request.description
            subscriptionPlanId = request.subscriptionPlanId
            status = "ACTIVE"
        }

        val saved = partnerRepository.save(entity)
        log.info("API partner registered: {} ({})", saved.partnerName, saved.partnerCode)
        return saved
    }

    fun getPartner(partnerId: Long): ApiPartnerEntity {
        return partnerRepository.findById(partnerId)
            .orElseThrow { IllegalArgumentException("Partner not found: $partnerId") }
    }

    fun getPartnersByDomain(domainId: Long): List<PartnerResponse> {
        return partnerRepository.findByDomainId(domainId).map { it.toResponse() }
    }

    fun getAllPartners(): List<PartnerResponse> {
        return partnerRepository.findAll().map { it.toResponse() }
    }

    @Transactional
    fun suspendPartner(partnerId: Long) {
        val partner = getPartner(partnerId)
        partner.status = "SUSPENDED"
        partnerRepository.save(partner)
        log.info("API partner suspended: {}", partnerId)
    }

    private fun ApiPartnerEntity.toResponse() = PartnerResponse(
        id = this.id!!,
        partnerName = this.partnerName,
        partnerCode = this.partnerCode,
        contactEmail = this.contactEmail,
        status = this.status,
        subscriptionPlanId = this.subscriptionPlanId,
        createdAt = this.createdAt?.toEpochMilli() ?: System.currentTimeMillis()
    )
}
