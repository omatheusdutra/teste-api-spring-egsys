package br.com.egsys.tasks.infrastructure.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
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
            SecurityContextHolder.getContext().authentication =
                UsernamePasswordAuthenticationToken(
                    authenticated.principal,
                    authenticated.credentials,
                    authenticated.principal.authorities,
                )
        }

        filterChain.doFilter(request, response)
    }

    private companion object {
        const val BEARER_PREFIX = "Bearer "
    }
}
