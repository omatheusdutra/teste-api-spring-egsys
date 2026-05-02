package br.com.egsys.tasks.infrastructure.security

import br.com.egsys.tasks.infrastructure.security.entity.RefreshTokenJpaEntity
import br.com.egsys.tasks.infrastructure.security.entity.UsuarioJpaEntity
import br.com.egsys.tasks.infrastructure.security.repository.SpringDataRefreshTokenRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.time.Clock
import java.time.Instant
import java.util.Base64
import java.util.UUID

@Service
class RefreshTokenService(
    private val repository: SpringDataRefreshTokenRepository,
    private val properties: SecurityProperties,
    private val clock: Clock,
) {
    private val secureRandom = SecureRandom()

    @Transactional
    fun create(user: UsuarioJpaEntity): RawRefreshToken {
        val familyId = UUID.randomUUID()
        return create(user, familyId)
    }

    @Transactional
    fun rotate(rawToken: String): RefreshTokenRotation {
        val now = clock.instant()
        val token =
            repository.findByTokenHash(TokenHashing.sha256Hex(rawToken))
                ?: throw InvalidTokenException("refresh token invalido")

        if (token.usedAt != null || token.revokedAt != null || !token.expiresAt.isAfter(now)) {
            token.familyId?.let(::revokeFamily)
            throw InvalidTokenException("refresh token reutilizado ou expirado")
        }

        token.usedAt = now
        val user = requireNotNull(token.user)
        val familyId = requireNotNull(token.familyId)

        return RefreshTokenRotation(user = user, refreshToken = create(user, familyId))
    }

    @Transactional
    fun revokeFamily(familyId: UUID) {
        val now = clock.instant()
        repository.findAllByFamilyId(familyId).forEach {
            if (it.revokedAt == null) {
                it.revokedAt = now
            }
        }
    }

    @Transactional
    fun revokeAllForUser(userId: UUID) {
        val now = clock.instant()
        repository.findAllByUserId(userId).forEach {
            if (it.revokedAt == null) {
                it.revokedAt = now
            }
        }
    }

    private fun create(
        user: UsuarioJpaEntity,
        familyId: UUID,
    ): RawRefreshToken {
        val raw = randomToken()
        val now = clock.instant()
        val entity =
            RefreshTokenJpaEntity(
                id = UUID.randomUUID(),
                familyId = familyId,
                user = user,
                tokenHash = TokenHashing.sha256Hex(raw),
                expiresAt = now.plus(properties.jwt.refreshTokenTtl),
                createdAt = now,
            )
        repository.save(entity)

        return RawRefreshToken(
            token = raw,
            familyId = familyId,
            expiresAt = entity.expiresAt,
        )
    }

    private fun randomToken(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}

data class RawRefreshToken(
    val token: String,
    val familyId: UUID,
    val expiresAt: Instant,
)

data class RefreshTokenRotation(
    val user: UsuarioJpaEntity,
    val refreshToken: RawRefreshToken,
)
