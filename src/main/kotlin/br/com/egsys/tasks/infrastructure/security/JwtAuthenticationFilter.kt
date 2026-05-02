package br.com.egsys.tasks.infrastructure.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(
    private val jwt: JwtService,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val header = request.getHeader(HttpHeaders.AUTHORIZATION)
        if (header?.startsWith(BEARER_PREFIX) == true) {
            val authenticated = jwt.authenticate(header.removePrefix(BEARER_PREFIX).trim())
            MDC.put(USER_ID_MDC_KEY, authenticated.principal.userId.toString())
            SecurityContextHolder.getContext().authentication =
                UsernamePasswordAuthenticationToken(
                    authenticated.principal,
                    authenticated.credentials,
                    authenticated.principal.authorities,
                )
        }

        try {
            filterChain.doFilter(request, response)
        } finally {
            MDC.remove(USER_ID_MDC_KEY)
        }
    }

    private companion object {
        const val BEARER_PREFIX = "Bearer "
        const val USER_ID_MDC_KEY = "userId"
    }
}
