package com.ntt.sysadmin.apipartner.application

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.ntt.sysadmin.apipartner.adapter.out.persistence.entity.ApiKeyEntity
import com.ntt.sysadmin.apipartner.adapter.out.persistence.repository.ApiKeyRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class IpWhitelistService(
    private val apiKeyRepository: ApiKeyRepository,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(IpWhitelistService::class.java)

    @Transactional
    fun updateIpWhitelist(apiKeyId: Long, ips: List<String>): ApiKeyEntity {
        val apiKey = apiKeyRepository.findById(apiKeyId)
            .orElseThrow { IllegalArgumentException("API key not found") }
        ips.forEach { ip -> require(isValidIpOrCidr(ip)) { "Invalid IP/CIDR: $ip" } }
        apiKey.ipWhitelistJson = if (ips.isNotEmpty()) objectMapper.writeValueAsString(ips) else null
        val saved = apiKeyRepository.save(apiKey)
        log.info("IP whitelist updated for API key {}: {} entries", apiKeyId, ips.size)
        return saved
    }

    fun getIpWhitelist(apiKeyId: Long): List<String> {
        val apiKey = apiKeyRepository.findById(apiKeyId)
            .orElseThrow { IllegalArgumentException("API key not found") }
        return if (apiKey.ipWhitelistJson != null)
            objectMapper.readValue(apiKey.ipWhitelistJson, object : TypeReference<List<String>>() {})
        else emptyList()
    }

    fun isIpAllowed(apiKeyId: Long, ip: String): Boolean {
        val whitelist = getIpWhitelist(apiKeyId)
        if (whitelist.isEmpty()) return true
        return whitelist.any { entry ->
            if (entry.contains("/")) isIpInCidr(ip, entry) else entry == ip
        }
    }

    private fun isValidIpOrCidr(input: String): Boolean {
        val pattern = Regex("""^(\d{1,3}\.){3}\d{1,3}(/\d{1,2})?$""")
        return pattern.matches(input)
    }

    private fun isIpInCidr(ip: String, cidr: String): Boolean {
        val parts = cidr.split("/")
        if (parts.size != 2) return ip == cidr
        val networkAddress = ipToLong(parts[0])
        val prefixLength = parts[1].toIntOrNull() ?: return false
        val mask = if (prefixLength == 0) 0L else (-1L shl (32 - prefixLength)) and 0xFFFFFFFFL
        return (ipToLong(ip) and mask) == (networkAddress and mask)
    }

    private fun ipToLong(ip: String): Long {
        return ip.split(".").fold(0L) { acc, part -> (acc shl 8) + (part.toIntOrNull() ?: 0) }
    }
}
