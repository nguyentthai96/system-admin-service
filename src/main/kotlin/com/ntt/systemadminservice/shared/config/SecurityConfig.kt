package com.ntt.systemadminservice.shared.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

/**
 * Security configuration for system-admin-service.
 *
 * JWT authentication is handled by DefaultSessionValidationFilter from base-security-starter,
 * which auto-registers as a servlet filter at order -1700 (before Spring Security chain).
 * This config only defines authorization rules and CORS.
 *
 * Admin endpoints require ADMIN or SUPER_ADMIN roles.
 * Uses STATELESS session — no server-side session storage.
 */
@Configuration
@EnableWebSecurity
class SecurityConfig(
    @Value("\${app.cors.allowed-origins:http://localhost:3000}")
    private val allowedOrigins: String
) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors { it.configurationSource(corsConfigurationSource()) }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .headers { headers ->
                headers
                    .httpStrictTransportSecurity { hsts ->
                        hsts.maxAgeInSeconds(31536000).includeSubDomains(true)
                    }
                    .contentTypeOptions { }
                    .frameOptions { it.deny() }
            }
            .authorizeHttpRequests { auth ->
                auth
                    // Health & readiness checks
                    .requestMatchers("/actuator/**").permitAll()
                    .requestMatchers("/health").permitAll()
                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    // Admin API — requires ADMIN or SUPER_ADMIN role
                    .requestMatchers("/api/admin/**").hasAnyRole("ADMIN", "SUPER_ADMIN")
                    // Menu/permission API — requires authenticated user
                    .requestMatchers("/api/menus/**").authenticated()
                    // Domain config API — requires ADMIN role
                    .requestMatchers("/api/domains/**").hasAnyRole("ADMIN", "SUPER_ADMIN")
                    // API partner management — requires SUPER_ADMIN
                    .requestMatchers("/api/partners/**").hasRole("SUPER_ADMIN")
                    // All other endpoints require authentication
                    .anyRequest().authenticated()
            }

        return http.build()
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration().apply {
            allowedOrigins = this@SecurityConfig.allowedOrigins.split(",").map { it.trim() }
            allowCredentials = true
            allowedHeaders = listOf("*")
            allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")
        }
        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", configuration)
        return source
    }
}
