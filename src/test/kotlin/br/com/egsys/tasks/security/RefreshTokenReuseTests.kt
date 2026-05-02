package br.com.egsys.tasks.security

import br.com.egsys.tasks.infrastructure.security.InvalidTokenException
import br.com.egsys.tasks.infrastructure.security.JwtProperties
import br.com.egsys.tasks.infrastructure.security.RefreshTokenService
import br.com.egsys.tasks.infrastructure.security.SecurityProperties
import br.com.egsys.tasks.infrastructure.security.TokenHashing
import br.com.egsys.tasks.infrastructure.security.UserRole
import br.com.egsys.tasks.infrastructure.security.entity.RefreshTokenJpaEntity
import br.com.egsys.tasks.infrastructure.security.entity.UsuarioJpaEntity
import br.com.egsys.tasks.infrastructure.security.repository.SpringDataRefreshTokenRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class RefreshTokenReuseTests {
    private val repository = mockk<SpringDataRefreshTokenRepository>(relaxed = true)
    private val now = Instant.parse("2026-05-01T12:00:00Z")
    private val service =
        RefreshTokenService(
            repository = repository,
            properties = SecurityProperties(jwt = JwtProperties(refreshTokenTtl = Duration.ofDays(7))),
            clock = Clock.fixed(now, ZoneOffset.UTC),
        )

    @Test
    fun `deve criar refresh token opaco com hash persistido`() {
        val captured = slot<RefreshTokenJpaEntity>()
        every { repository.save(capture(captured)) } answers { firstArg() }

        val created = service.create(usuario())

        created.token.length shouldBe 43
        captured.captured.tokenHash shouldBe TokenHashing.sha256Hex(created.token)
        captured.captured.expiresAt shouldBe now.plus(Duration.ofDays(7))
    }

    @Test
    fun `deve rotacionar refresh token valido`() {
        val familyId = UUID.fromString("018f95df-0c7b-7af2-a199-447f82f36990")
        val current = refreshToken(familyId = familyId)
        every { repository.findByTokenHash(TokenHashing.sha256Hex("raw-refresh-token")) } returns current
        every { repository.save(any()) } answers { firstArg() }

        val rotation = service.rotate("raw-refresh-token")

        current.usedAt shouldBe now
        rotation.user.id shouldBe current.user?.id
        rotation.refreshToken.familyId shouldBe familyId
        verify(exactly = 1) { repository.save(any()) }
    }

    @Test
    fun `deve rejeitar refresh token reutilizado e revogar familia`() {
        val familyId = UUID.fromString("018f95df-0c7b-7af2-a199-447f82f36990")
        val reused =
            refreshToken(familyId = familyId).apply {
                usedAt = now.minusSeconds(10)
            }
        val sibling = refreshToken(familyId = familyId)

        every { repository.findByTokenHash(TokenHashing.sha256Hex("raw-refresh-token")) } returns reused
        every { repository.findAllByFamilyId(familyId) } returns listOf(reused, sibling)

        shouldThrow<InvalidTokenException> {
            service.rotate("raw-refresh-token")
        }

        reused.revokedAt.shouldNotBeNull()
        sibling.revokedAt.shouldNotBeNull()
    }

    @Test
    fun `deve rejeitar refresh token expirado e revogar familia`() {
        val familyId = UUID.fromString("018f95df-0c7b-7af2-a199-447f82f36991")
        val expired =
            refreshToken(familyId = familyId).apply {
                expiresAt = now.minusSeconds(1)
            }
        every { repository.findByTokenHash(TokenHashing.sha256Hex("expired-refresh-token")) } returns expired
        every { repository.findAllByFamilyId(familyId) } returns listOf(expired)

        shouldThrow<InvalidTokenException> {
            service.rotate("expired-refresh-token")
        }

        expired.revokedAt.shouldNotBeNull()
    }

    private fun refreshToken(familyId: UUID): RefreshTokenJpaEntity =
        RefreshTokenJpaEntity(
            id = UUID.randomUUID(),
            familyId = familyId,
            user =
                UsuarioJpaEntity(
                    id = UUID.fromString("018f95df-0c7b-7af2-a199-447f82f36914"),
                    email = "ana@example.com",
                    passwordHash = "hash",
                    role = UserRole.ROLE_USER,
                    criadaEm = now,
                    atualizadaEm = now,
                ),
            tokenHash = "hash",
            expiresAt = now.plusSeconds(3600),
            createdAt = now,
        )

    private fun usuario(): UsuarioJpaEntity =
        UsuarioJpaEntity(
            id = UUID.fromString("018f95df-0c7b-7af2-a199-447f82f36914"),
            email = "ana@example.com",
            passwordHash = "hash",
            role = UserRole.ROLE_USER,
            criadaEm = now,
            atualizadaEm = now,
        )
}
