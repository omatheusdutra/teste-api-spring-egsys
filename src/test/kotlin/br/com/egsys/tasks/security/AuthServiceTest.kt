package br.com.egsys.tasks.security

import br.com.egsys.tasks.infrastructure.security.AuthService
import br.com.egsys.tasks.infrastructure.security.AuthenticatedPrincipal
import br.com.egsys.tasks.infrastructure.security.EmailAlreadyRegisteredException
import br.com.egsys.tasks.infrastructure.security.InvalidCredentialsException
import br.com.egsys.tasks.infrastructure.security.IssuedAccessToken
import br.com.egsys.tasks.infrastructure.security.JwtService
import br.com.egsys.tasks.infrastructure.security.PasswordPolicy
import br.com.egsys.tasks.infrastructure.security.RateLimiterService
import br.com.egsys.tasks.infrastructure.security.RawRefreshToken
import br.com.egsys.tasks.infrastructure.security.RefreshTokenRotation
import br.com.egsys.tasks.infrastructure.security.RefreshTokenService
import br.com.egsys.tasks.infrastructure.security.SecurityProperties
import br.com.egsys.tasks.infrastructure.security.UserRole
import br.com.egsys.tasks.infrastructure.security.entity.UsuarioJpaEntity
import br.com.egsys.tasks.infrastructure.security.repository.SpringDataUsuarioRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class AuthServiceTest {
    private val usuarios = mockk<SpringDataUsuarioRepository>()
    private val passwordEncoder = mockk<PasswordEncoder>()
    private val passwordPolicy = mockk<PasswordPolicy>()
    private val refreshTokens = mockk<RefreshTokenService>()
    private val jwt = mockk<JwtService>()
    private val rateLimiter = mockk<RateLimiterService>()
    private val now = Instant.parse("2026-05-01T12:00:00Z")
    private val userId = UUID.fromString("018f95df-0c7b-7af2-a199-447f82f36914")
    private val service =
        AuthService(
            usuarios = usuarios,
            passwordEncoder = passwordEncoder,
            passwordPolicy = passwordPolicy,
            refreshTokens = refreshTokens,
            jwt = jwt,
            rateLimiter = rateLimiter,
            properties = SecurityProperties(passwordPepper = "pepper"),
            clock = Clock.fixed(now, ZoneOffset.UTC),
        )

    @BeforeEach
    fun setUp() {
        justRun { passwordPolicy.validate(any()) }
        justRun { rateLimiter.consume(any(), any(), any()) }
        every { passwordEncoder.encode(any()) } answers { "argon:${firstArg<String>()}" }
        every { jwt.issueAccessToken(any()) } answers {
            val principal = firstArg<AuthenticatedPrincipal>()
            IssuedAccessToken(
                token = "access-${principal.userId}",
                jti = "jti",
                expiresAt = now.plusSeconds(900),
            )
        }
    }

    @Test
    fun `register creates user with normalized email and Argon peppered password`() {
        val captured = slot<UsuarioJpaEntity>()
        every { usuarios.existsByEmailIgnoreCase("ana@example.com") } returns false
        every { usuarios.save(capture(captured)) } answers {
            captured.captured.apply { id = userId }
        }
        every { refreshTokens.create(any()) } returns refresh()

        val tokens = service.register(" Ana@Example.COM ", "Senha-forte-123!")

        captured.captured.email shouldBe "ana@example.com"
        captured.captured.passwordHash shouldBe "argon:Senha-forte-123!pepper"
        tokens.accessToken shouldBe "access-$userId"
        tokens.refreshToken shouldBe "refresh"
    }

    @Test
    fun `register rejects duplicated email`() {
        every { usuarios.existsByEmailIgnoreCase("ana@example.com") } returns true

        shouldThrow<EmailAlreadyRegisteredException> {
            service.register("ana@example.com", "Senha-forte-123!")
        }

        verify(exactly = 0) { usuarios.save(any()) }
    }

    @Test
    fun `login returns generic error for missing user`() {
        every { usuarios.findByEmailIgnoreCase("ana@example.com") } returns null
        every { passwordEncoder.matches("wrongpepper", any()) } returns false

        shouldThrow<InvalidCredentialsException> {
            service.login("ana@example.com", "wrong")
        }
    }

    @Test
    fun `login issues token pair for valid password`() {
        val user = usuario()
        every { usuarios.findByEmailIgnoreCase("ana@example.com") } returns user
        every { passwordEncoder.matches("Senha-forte-123!pepper", "hash") } returns true
        every { refreshTokens.create(user) } returns refresh()

        val tokens = service.login("ana@example.com", "Senha-forte-123!")

        tokens.accessToken shouldBe "access-$userId"
        tokens.refreshToken shouldBe "refresh"
    }

    @Test
    fun `refresh rotates refresh token`() {
        val user = usuario()
        every { refreshTokens.rotate("old-refresh") } returns RefreshTokenRotation(user, refresh())

        service.refresh("old-refresh").refreshToken shouldBe "refresh"
    }

    @Test
    fun `logout and revoke all delegate to token stores`() {
        justRun { jwt.revoke("jti", now.plusSeconds(60)) }
        justRun { refreshTokens.revokeAllForUser(userId) }

        service.logout("jti", now.plusSeconds(60))
        service.revokeAll(userId)

        verify(exactly = 1) { jwt.revoke("jti", now.plusSeconds(60)) }
        verify(exactly = 1) { refreshTokens.revokeAllForUser(userId) }
    }

    private fun usuario(): UsuarioJpaEntity =
        UsuarioJpaEntity(
            id = userId,
            email = "ana@example.com",
            passwordHash = "hash",
            role = UserRole.ROLE_USER,
            criadaEm = now,
            atualizadaEm = now,
        )

    private fun refresh(): RawRefreshToken =
        RawRefreshToken(
            token = "refresh",
            familyId = UUID.fromString("018f95df-0c7b-7af2-a199-447f82f36990"),
            expiresAt = now.plusSeconds(3600),
        )
}
