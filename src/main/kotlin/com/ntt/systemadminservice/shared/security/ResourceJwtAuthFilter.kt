package com.ntt.systemadminservice.shared.security

import com.ntt.basecore.autoconfigure.security.jwt.JwtTokenExtractor
import io.jsonwebtoken.Claims
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Resource server JWT authentication filter for system-admin-service.
 *
 * Validates JWT tokens issued by auth-service using [JwtTokenExtractor] from base-security-starter.
 * Extracts subject, roles, and permissions from validated claims and sets SecurityContext.
 *
 * This is a lightweight filter — no blacklist, no claim chain, no fingerprint validation.
 * Those concerns are handled by auth-service (the token issuer).
 */
@Component
class ResourceJwtAuthFilter(
    private val jwtTokenExtractor: JwtTokenExtractor
) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(ResourceJwtAuthFilter::class.java)

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
            val result = jwtTokenExtractor.validate(token)

            if (!result.isValid) {
                log.debug("JWT validation failed: {}", result.errorMessage)
                filterChain.doFilter(request, response)
                return
            }

            val claims = result.principal as? Claims
            if (claims == null) {
                log.debug("JWT principal is not Claims type")
                filterChain.doFilter(request, response)
                return
            }

            val userId = claims.subject
            val roles = (claims["roles"] as? List<*>)?.map { "ROLE_$it" } ?: emptyList()
            val permissions = (claims["permissions"] as? List<*>)?.map { it.toString() } ?: emptyList()

            val authorities = roles.map { SimpleGrantedAuthority(it) } +
                permissions.map { SimpleGrantedAuthority("PERM_$it") }

            val authentication = UsernamePasswordAuthenticationToken(
                userId, null, authorities
            )
            authentication.details = mapOf(
                "activeDomain" to (claims["active_domain"] ?: ""),
                "domains" to (claims["domains"] ?: emptyList<String>()),
                "username" to (claims["username"] ?: "")
            )

            SecurityContextHolder.getContext().authentication = authentication

        } catch (e: Exception) {
            log.debug("JWT processing failed: {}", e.message)
        }

        filterChain.doFilter(request, response)
    }
}
