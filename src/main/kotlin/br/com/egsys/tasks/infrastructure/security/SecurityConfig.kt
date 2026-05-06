package br.com.egsys.tasks.infrastructure.security

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(SecurityProperties::class)
class SecurityConfig(
    private val properties: SecurityProperties,
    private val objectMapper: ObjectMapper,
) {
    @Bean
    fun passwordEncoder(): PasswordEncoder = Argon2PasswordEncoder(16, 32, 1, 65_536, 3)

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        jwtAuthenticationFilter: JwtAuthenticationFilter,
        rateLimitingFilter: RateLimitingFilter,
    ): SecurityFilterChain =
        http
            // Stateless API: JWT travels in Authorization header, so browser CSRF cookies are not used.
            .csrf { it.disable() }
            .cors { it.configurationSource(corsConfigurationSource()) }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .headers {
                it.contentSecurityPolicy { csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY) }
                it.httpStrictTransportSecurity { hsts ->
                    hsts.includeSubDomains(true).preload(true).maxAgeInSeconds(31_536_000)
                }
                it.frameOptions { frame -> frame.deny() }
                it.referrerPolicy { referrer ->
                    referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)
                }
                it.permissionsPolicyHeader { permissions ->
                    permissions.policy("geolocation=(), microphone=(), camera=()")
                }
                it.cacheControl { }
            }.exceptionHandling {
                it.authenticationEntryPoint { _, response, _ ->
                    writeProblem(response, HttpStatus.UNAUTHORIZED, "Nao autenticado", "Token ausente ou invalido")
                }
                it.accessDeniedHandler { _, response, _ ->
                    writeProblem(response, HttpStatus.FORBIDDEN, "Acesso negado", "Permissao insuficiente")
                }
            }.authorizeHttpRequests {
                // Public by design: friendly discovery page with links to API docs and local evaluation flow.
                it.requestMatchers(HttpMethod.GET, "/").permitAll()
                it.requestMatchers(HttpMethod.GET, "/index.html").permitAll()
                it.requestMatchers(HttpMethod.GET, "/home.js").permitAll()
                it.requestMatchers(HttpMethod.GET, "/egsys-logo.svg").permitAll()
                it.requestMatchers(HttpMethod.GET, "/assets/**").permitAll()
                // Public by design: platform load balancers need a lightweight readiness signal.
                // Health details remain disabled; metrics and business endpoints stay authenticated.
                it.requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
                // Playground is feature-flagged by egsys.playground.enabled. The static resource lives outside
                // /static/, so there is no path that bypasses the property-controlled controller.
                it.requestMatchers(HttpMethod.GET, "/playground", "/playground/**").permitAll()
                // Public by design: account bootstrap and token rotation must be reachable before authentication.
                it.requestMatchers(HttpMethod.POST, "/api/v1/auth/register").permitAll()
                it.requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                it.requestMatchers(HttpMethod.POST, "/api/v1/auth/refresh").permitAll()
                // Public by design for evaluator discovery; protected business endpoints still require JWT.
                it.requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                it.anyRequest().authenticated()
            }.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
            .addFilterAfter(rateLimitingFilter, JwtAuthenticationFilter::class.java)
            .build()

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration =
            CorsConfiguration().apply {
                allowedOrigins = properties.allowedOrigins
                allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")
                allowedHeaders = listOf("Authorization", "Content-Type", "X-Correlation-Id")
                exposedHeaders = listOf("Location", "Retry-After")
                allowCredentials = false
                maxAge = 3_600
            }
        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/**", configuration)
        }
    }

    private fun writeProblem(
        response: jakarta.servlet.http.HttpServletResponse,
        status: HttpStatus,
        title: String,
        detail: String,
    ) {
        response.status = status.value()
        response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
        objectMapper.writeValue(
            response.outputStream,
            ProblemDetail.forStatusAndDetail(status, detail).apply { this.title = title },
        )
    }

    private companion object {
        const val CONTENT_SECURITY_POLICY =
            "default-src 'self'; " +
                "script-src 'self'; " +
                "style-src 'self' 'unsafe-inline'; " +
                "img-src 'self' data: https:; " +
                "font-src 'self' data:; " +
                "connect-src 'self'; " +
                "base-uri 'self'; " +
                "form-action 'self'; " +
                "frame-ancestors 'none'"
    }
}
