package br.com.egsys.tasks.infrastructure.security

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Duration

@Component
class RateLimitingFilter(
    private val rateLimiter: RateLimiterService,
    private val objectMapper: ObjectMapper,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        try {
            consume(request)
            filterChain.doFilter(request, response)
        } catch (ex: RateLimitExceededException) {
            response.status = HttpStatus.TOO_MANY_REQUESTS.value()
            response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
            response.setHeader(HttpHeaders.RETRY_AFTER, ex.retryAfterSeconds.toString())
            objectMapper.writeValue(
                response.outputStream,
                ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, "Tente novamente mais tarde").apply {
                    title = "Limite de requisicoes excedido"
                },
            )
        }
    }

    private fun consume(request: HttpServletRequest) {
        val path = request.requestURI
        if (path == "/api/v1/auth/login") {
            rateLimiter.consume("login:ip:${clientIp(request)}", LOGIN_PER_IP_LIMIT, Duration.ofMinutes(1))
            return
        }

        val principal = SecurityContextHolder.getContext().authentication?.principal
        if (principal is AuthenticatedPrincipal) {
            rateLimiter.consume("auth:user:${principal.userId}", AUTHENTICATED_LIMIT, Duration.ofMinutes(1))
        } else if (path.startsWith("/api/")) {
            rateLimiter.consume("public:ip:${clientIp(request)}", PUBLIC_LIMIT, Duration.ofMinutes(1))
        }
    }

    private fun clientIp(request: HttpServletRequest): String =
        request
            .getHeader("X-Forwarded-For")
            ?.substringBefore(",")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: request.remoteAddr

    private companion object {
        const val LOGIN_PER_IP_LIMIT = 5L
        const val AUTHENTICATED_LIMIT = 100L
        const val PUBLIC_LIMIT = 30L
    }
}
