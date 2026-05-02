package br.com.egsys.tasks.security

import br.com.egsys.tasks.application.usecase.ListarCategoriasUseCase
import br.com.egsys.tasks.infrastructure.security.AuthService
import br.com.egsys.tasks.infrastructure.security.AuthenticatedJwt
import br.com.egsys.tasks.infrastructure.security.AuthenticatedPrincipal
import br.com.egsys.tasks.infrastructure.security.JwtCredentials
import br.com.egsys.tasks.infrastructure.security.JwtService
import br.com.egsys.tasks.infrastructure.security.RateLimitExceededException
import br.com.egsys.tasks.infrastructure.security.RateLimiterService
import br.com.egsys.tasks.infrastructure.security.SecurityConfig
import br.com.egsys.tasks.infrastructure.security.UserRole
import br.com.egsys.tasks.web.controller.AuthController
import br.com.egsys.tasks.web.controller.CategoriaController
import br.com.egsys.tasks.web.exception.ApiExceptionHandler
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.justRun
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.UUID

@WebMvcTest(controllers = [CategoriaController::class, AuthController::class])
@Import(SecurityConfig::class, ApiExceptionHandler::class)
@Suppress("UnusedPrivateProperty")
class AuthorizationAndHeadersTests {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var listarCategorias: ListarCategoriasUseCase

    @MockkBean
    private lateinit var authService: AuthService

    @MockkBean
    private lateinit var jwtService: JwtService

    @MockkBean
    private lateinit var rateLimiter: RateLimiterService

    @BeforeEach
    fun setUp() {
        justRun { rateLimiter.consume(any(), any(), any()) }
    }

    @Test
    fun `chamada sem token retorna 401 e headers de seguranca`() {
        mockMvc
            .perform(get("/api/v1/categorias"))
            .andExpect(status().isUnauthorized)
            .andExpect(header().string("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'"))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(header().string("X-Frame-Options", "DENY"))
            .andExpect(header().string("Referrer-Policy", "no-referrer"))
            .andExpect(header().string("Permissions-Policy", "geolocation=(), microphone=(), camera=()"))
            .andExpect(header().string("Cache-Control", "no-cache, no-store, max-age=0, must-revalidate"))
            .andExpect(jsonPath("$.title").value("Nao autenticado"))
    }

    @Test
    fun `USER nao acessa endpoint ADMIN`() {
        every { jwtService.authenticate("valid-user") } returns authenticated(UserRole.ROLE_USER)

        mockMvc
            .perform(
                post("/api/v1/auth/revoke-all/018f95df-0c7b-7af2-a199-447f82f36999")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer valid-user"),
            ).andExpect(status().isForbidden)
            .andExpect(jsonPath("$.title").value("Acesso negado"))
    }

    @Test
    fun `ADMIN acessa endpoint ADMIN`() {
        every { jwtService.authenticate("valid-admin") } returns authenticated(UserRole.ROLE_ADMIN)
        justRun { authService.revokeAll(any()) }

        mockMvc
            .perform(
                post("/api/v1/auth/revoke-all/018f95df-0c7b-7af2-a199-447f82f36999")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer valid-admin"),
            ).andExpect(status().isNoContent)
    }

    @Test
    fun `rate limit retorna 429 com Retry-After`() {
        every { rateLimiter.consume(match { it.startsWith("login:ip:") }, any(), any()) } throws
            RateLimitExceededException(60)

        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"email":"ana@example.com","password":"Senha-forte-123!"}"""),
            ).andExpect(status().isTooManyRequests)
            .andExpect(header().string(HttpHeaders.RETRY_AFTER, "60"))
            .andExpect(jsonPath("$.title").value("Limite de requisicoes excedido"))
    }

    @Test
    fun `prometheus exige autenticacao`() {
        mockMvc
            .perform(get("/actuator/prometheus"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.title").value("Nao autenticado"))
    }

    private fun authenticated(role: UserRole): AuthenticatedJwt {
        val principal =
            AuthenticatedPrincipal(
                userId = UUID.fromString("018f95df-0c7b-7af2-a199-447f82f36914"),
                email = "ana@example.com",
                role = role,
            )
        return AuthenticatedJwt(
            principal = principal,
            credentials = JwtCredentials("jti", Instant.parse("2030-05-01T12:00:00Z")),
        )
    }
}
