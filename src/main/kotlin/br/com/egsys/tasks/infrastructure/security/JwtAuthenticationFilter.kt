package br.com.egsys.tasks.infrastructure.security

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(
    private val jwt: JwtService,
    private val objectMapper: ObjectMapper,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        try {
            val header = request.getHeader(HttpHeaders.AUTHORIZATION)
            if (header?.startsWith(BEARER_PREFIX) == true) {
                authenticate(header)
            }
            filterChain.doFilter(request, response)
        } catch (_: InvalidTokenException) {
            SecurityContextHolder.clearContext()
            response.status = HttpStatus.UNAUTHORIZED.value()
            response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
            objectMapper.writeValue(
                response.outputStream,
                ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Token ausente ou invalido").apply {
                    title = "Nao autenticado"
                },
            )
        } finally {
            MDC.remove(USER_ID_MDC_KEY)
        }
    }

    private fun authenticate(header: String) {
        val authenticated = jwt.authenticate(header.removePrefix(BEARER_PREFIX).trim())
        MDC.put(USER_ID_MDC_KEY, authenticated.principal.userId.toString())
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(
                authenticated.principal,
                authenticated.credentials,
                authenticated.principal.authorities,
            )
    }

    private companion object {
        const val BEARER_PREFIX = "Bearer "
        const val USER_ID_MDC_KEY = "userId"
    }
}
