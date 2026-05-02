package br.com.egsys.tasks.infrastructure.observability

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class CorrelationIdFilter : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val correlationId =
            request
                .getHeader(HEADER_NAME)
                ?.takeIf { it.isSafeCorrelationId() }
                ?: UUID.randomUUID().toString()
        response.setHeader(HEADER_NAME, correlationId)
        MDC.put(MDC_KEY, correlationId)
        try {
            filterChain.doFilter(request, response)
        } finally {
            MDC.remove(MDC_KEY)
        }
    }

    private fun String.isSafeCorrelationId(): Boolean = length in MIN_LENGTH..MAX_LENGTH && matches(SAFE_VALUE)

    companion object {
        const val HEADER_NAME = "X-Correlation-Id"
        const val MDC_KEY = "correlationId"
        private const val MIN_LENGTH = 8
        private const val MAX_LENGTH = 128
        private val SAFE_VALUE = Regex("^[A-Za-z0-9._:-]+$")
    }
}
