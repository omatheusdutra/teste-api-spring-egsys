package br.com.egsys.tasks.security

import br.com.egsys.tasks.infrastructure.security.AuthenticatedPrincipal
import br.com.egsys.tasks.infrastructure.security.RateLimitExceededException
import br.com.egsys.tasks.infrastructure.security.RateLimiterService
import br.com.egsys.tasks.infrastructure.security.RateLimitingFilter
import br.com.egsys.tasks.infrastructure.security.UserRole
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.time.Duration
import java.util.UUID

class RateLimitingFilterTest {
    private val rateLimiter = mockk<RateLimiterService>()
    private val filter = RateLimitingFilter(rateLimiter, ObjectMapper())

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `limita login por IP considerando primeiro X-Forwarded-For`() {
        justRun { rateLimiter.consume(any(), any(), any()) }
        val request =
            MockHttpServletRequest("POST", "/api/v1/auth/login").apply {
                addHeader("X-Forwarded-For", "203.0.113.10, 10.0.0.1")
            }
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, MockFilterChain())

        response.status shouldBe 200
        verify(exactly = 1) {
            rateLimiter.consume("login:ip:203.0.113.10", 5L, Duration.ofMinutes(1))
        }
    }

    @Test
    fun `limita endpoints autenticados por usuario`() {
        justRun { rateLimiter.consume(any(), any(), any()) }
        val userId = UUID.fromString("018f95df-0c7b-7af2-a199-447f82f36914")
        val principal = AuthenticatedPrincipal(userId, "ana@example.com", UserRole.ROLE_USER)
        SecurityContextHolder.getContext().authentication =
            TestingAuthenticationToken(principal, "jwt", principal.authorities)
        val request = MockHttpServletRequest("GET", "/api/v1/tarefas")
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, MockFilterChain())

        verify(exactly = 1) {
            rateLimiter.consume("auth:user:$userId", 100L, Duration.ofMinutes(1))
        }
    }

    @Test
    fun `limita endpoints publicos por IP remoto`() {
        justRun { rateLimiter.consume(any(), any(), any()) }
        val request =
            MockHttpServletRequest("GET", "/api/v1/categorias").apply {
                remoteAddr = "198.51.100.20"
            }
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, MockFilterChain())

        verify(exactly = 1) {
            rateLimiter.consume("public:ip:198.51.100.20", 30L, Duration.ofMinutes(1))
        }
    }

    @Test
    fun `retorna ProblemDetail com Retry-After quando limite estoura`() {
        every { rateLimiter.consume(any(), any(), any()) } throws RateLimitExceededException(17)
        val request = MockHttpServletRequest("POST", "/api/v1/auth/login")
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, MockFilterChain())

        response.status shouldBe 429
        response.contentType shouldBe MediaType.APPLICATION_PROBLEM_JSON_VALUE
        response.getHeader(HttpHeaders.RETRY_AFTER) shouldBe "17"
        response.contentAsString.contains("Limite de requisicoes excedido") shouldBe true
    }
}
