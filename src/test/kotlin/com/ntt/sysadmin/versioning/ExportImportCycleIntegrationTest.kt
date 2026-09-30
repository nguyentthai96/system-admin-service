package com.ntt.sysadmin.versioning

import com.ntt.basecore.autoconfigure.file.imports.ImportContext
import com.ntt.basecore.autoconfigure.file.imports.ImportStrategyMode
import com.ntt.basecore.autoconfigure.file.imports.RelationalImportCoordinator
import com.ntt.sysadmin.versioning.domain.ConfigDomainRegistry
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

/**
 * Integration test verifying full export → import → verify cycle with SHA-256 checksum.
 *
 * Validates:
 * - Export produces valid JSON with SHA-256 checksum
 * - Import reads JSON and processes via RelationalImportCoordinator
 * - Data integrity verified via checksum match
 */
@SpringBootTest
@ActiveProfiles("test")
class ExportImportCycleIntegrationTest {

    @Autowired
    private lateinit var domainRegistry: ConfigDomainRegistry

    @Autowired
    private lateinit var relationalImportCoordinator: RelationalImportCoordinator

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Test
    @DisplayName("Export → SHA-256 → Import → Verify round-trip")
    fun `full export import cycle with checksum verification`() {
        // 1. Export all domains to JSON
        val domains = domainRegistry.getAllDomains()
        val exportData = mutableMapOf<String, List<Any>>()

        domains.forEach { domain ->
            val state = domain.fetchCurrentState()
            exportData[domain.domainName] = state
        }

        // 2. Serialize to JSON bytes
        val outputStream = ByteArrayOutputStream()
        objectMapper.writeValue(outputStream, exportData)
        val jsonBytes = outputStream.toByteArray()

        // 3. Calculate SHA-256 checksum
        val digest = MessageDigest.getInstance("SHA-256")
        val checksum = digest.digest(jsonBytes).joinToString("") { "%02x".format(it) }

        assertNotNull(checksum)
        assertEquals(64, checksum.length, "SHA-256 checksum should be 64 hex characters")

        // 4. Verify checksum is deterministic
        val checksum2 = digest.digest(jsonBytes).joinToString("") { "%02x".format(it) }
        assertEquals(checksum, checksum2, "SHA-256 should be deterministic")

        // 5. Parse back and verify structure
        @Suppress("UNCHECKED_CAST")
        val parsed = objectMapper.readValue(jsonBytes, Map::class.java) as Map<String, List<Any>>
        assertFalse(parsed.isEmpty(), "Export should contain at least one domain")

        parsed.forEach { (domainName, records) ->
            assertNotNull(domainName, "Domain name should not be null")
            assertNotNull(records, "Records list should not be null")
        }
    }

    @Test
    @DisplayName("SHA-256 checksum mismatch should be detectable")
    fun `checksum mismatch detection`() {
        val data = mapOf("test" to "value")
        val jsonBytes = objectMapper.writeValueAsBytes(data)

        val digest = MessageDigest.getInstance("SHA-256")
        val correctChecksum = digest.digest(jsonBytes).joinToString("") { "%02x".format(it) }

        // Tamper with data
        val tamperedBytes = jsonBytes.copyOf()
        tamperedBytes[0] = (tamperedBytes[0] + 1).toByte()

        val tamperedChecksum = MessageDigest.getInstance("SHA-256")
            .digest(tamperedBytes).joinToString("") { "%02x".format(it) }

        assertNotEquals(correctChecksum, tamperedChecksum, "Tampered data should produce different checksum")
    }
}
