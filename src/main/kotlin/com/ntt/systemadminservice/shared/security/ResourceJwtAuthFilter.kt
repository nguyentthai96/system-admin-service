package com.ntt.systemadminservice.shared.security

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyFactory
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * JWT Authentication Filter for system-admin-service.
 *
 * Validates JWT tokens issued by auth-service using RSA public key (RS256).
 * Lightweight — no blacklist check, no event recording (those are auth-service concerns).
 *
 * Usage: Configure `app.security.jwt.public-key-path` to point to auth-service's RSA public key PEM.
 */
@Component
class ResourceJwtAuthFilter(
    @Value("\${app.security.jwt.public-key-path:}")
    private val publicKeyPath: String,

    @Value("\${app.security.jwt.secret-key:}")
    private val secretKey: String,

    @Value("\${app.security.jwt.clock-skew-seconds:60}")
    private val clockSkewSeconds: Long,

    @Value("\${app.security.jwt.issuer:auth-service}")
    private val issuer: String
) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(ResourceJwtAuthFilter::class.java)

    /** Lazily loaded RSA public key for token verification. */
    private val rsaPublicKey: RSAPublicKey? by lazy {
        if (publicKeyPath.isNotBlank()) {
            try {
                loadPublicKey(publicKeyPath)
            } catch (e: Exception) {
                log.error("Failed to load RSA public key from {}: {}", publicKeyPath, e.message)
                null
            }
        } else {
            null
        }
    }

    /** HMAC fallback key for development/testing. */
    private val hmacKey: javax.crypto.SecretKey? by lazy {
        if (secretKey.isNotBlank()) {
            io.jsonwebtoken.security.Keys.hmacShaKeyFor(secretKey.toByteArray())
        } else null
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val authHeader = request.getHeader("Authorization")

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response)
            return
        }

        val token = authHeader.substring(7)

        try {
            val claims = parseToken(token)

            // Extract authorities from JWT claims
            val roles = (claims["roles"] as? List<*>)?.map { "ROLE_$it" } ?: emptyList()
            val permissions = (claims["permissions"] as? List<*>)?.map { "PERM_$it" } ?: emptyList()

            val authorities = roles.map { SimpleGrantedAuthority(it) } +
                    permissions.map { SimpleGrantedAuthority(it) }

            val authentication = UsernamePasswordAuthenticationToken(
                claims.subject, null, authorities
            )
            authentication.details = mapOf(
                "activeDomain" to (claims["active_domain"] ?: ""),
                "domains" to (claims["domains"] ?: emptyList<String>()),
                "username" to (claims["username"] ?: "")
            )

            SecurityContextHolder.getContext().authentication = authentication

        } catch (e: Exception) {
            log.debug("JWT validation failed: {}", e.message)
            // Continue without authentication — secured endpoints will reject
        }

        filterChain.doFilter(request, response)
    }

    private fun parseToken(token: String): Claims {
        // Try RSA public key first
        val pubKey = rsaPublicKey
        if (pubKey != null) {
            try {
                return Jwts.parser()
                    .verifyWith(pubKey)
                    .clockSkewSeconds(clockSkewSeconds)
                    .build()
                    .parseSignedClaims(token)
                    .payload
            } catch (_: Exception) {
                // Fall through to HMAC
            }
        }

        // HMAC fallback (development)
        val hk = hmacKey
        if (hk != null) {
            return Jwts.parser()
                .verifyWith(hk)
                .clockSkewSeconds(clockSkewSeconds)
                .build()
                .parseSignedClaims(token)
                .payload
        }

        throw IllegalStateException("No verification key configured for JWT validation")
    }

    private fun loadPublicKey(path: String): RSAPublicKey {
        val kf = KeyFactory.getInstance("RSA")
        val publicKeyPem = Files.readString(Path.of(path))
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("\\s+".toRegex(), "")
        return kf.generatePublic(
            X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyPem))
        ) as RSAPublicKey
    }
}
