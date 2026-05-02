package br.com.egsys.tasks.infrastructure.security

import br.com.egsys.tasks.infrastructure.security.entity.UsuarioJpaEntity
import br.com.egsys.tasks.infrastructure.security.repository.SpringDataUsuarioRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.util.Locale
import java.util.UUID

@Service
@Suppress("LongParameterList")
class AuthService(
    private val usuarios: SpringDataUsuarioRepository,
    private val passwordEncoder: PasswordEncoder,
    private val passwordPolicy: PasswordPolicy,
    private val refreshTokens: RefreshTokenService,
    private val jwt: JwtService,
    private val rateLimiter: RateLimiterService,
    private val properties: SecurityProperties,
    private val clock: Clock,
) {
    private val dummyPasswordHash: String by lazy {
        passwordEncoder.encode(withPepper("Dummy-password-123!"))
    }

    @Transactional
    fun register(
        email: String,
        password: String,
    ): TokenPair {
        val normalizedEmail = normalizeEmail(email)
        passwordPolicy.validate(password)
        if (usuarios.existsByEmailIgnoreCase(normalizedEmail)) {
            throw EmailAlreadyRegisteredException()
        }

        val now = clock.instant()
        val user =
            usuarios.save(
                UsuarioJpaEntity(
                    id = UUID.randomUUID(),
                    email = normalizedEmail,
                    passwordHash = passwordEncoder.encode(withPepper(password)),
                    role = UserRole.ROLE_USER,
                    criadaEm = now,
                    atualizadaEm = now,
                ),
            )

        return tokenPair(user, refreshTokens.create(user))
    }

    @Transactional
    fun login(
        email: String,
        password: String,
    ): TokenPair {
        val normalizedEmail = normalizeEmail(email)
        rateLimiter.consume("login:user:$normalizedEmail", LOGIN_PER_USER_LIMIT, Duration.ofHours(1))

        val user = usuarios.findByEmailIgnoreCase(normalizedEmail)
        val passwordHash = user?.passwordHash ?: dummyPasswordHash
        val passwordMatches = passwordEncoder.matches(withPepper(password), passwordHash)
        if (user == null || !passwordMatches) {
            throw InvalidCredentialsException()
        }

        return tokenPair(user, refreshTokens.create(user))
    }

    @Transactional
    fun refresh(refreshToken: String): TokenPair {
        val rotation = refreshTokens.rotate(refreshToken)
        return tokenPair(rotation.user, rotation.refreshToken)
    }

    @Transactional
    fun revokeAll(userId: UUID) {
        refreshTokens.revokeAllForUser(userId)
    }

    fun logout(
        jti: String,
        expiresAt: java.time.Instant,
    ) {
        jwt.revoke(jti, expiresAt)
    }

    private fun tokenPair(
        user: UsuarioJpaEntity,
        refreshToken: RawRefreshToken,
    ): TokenPair {
        val principal =
            AuthenticatedPrincipal(
                userId = requireNotNull(user.id),
                email = user.email,
                role = user.role,
            )
        val accessToken = jwt.issueAccessToken(principal)
        return TokenPair(
            accessToken = accessToken.token,
            accessExpiresAt = accessToken.expiresAt,
            refreshToken = refreshToken.token,
            refreshExpiresAt = refreshToken.expiresAt,
            tokenType = "Bearer",
        )
    }

    private fun withPepper(raw: String): String = raw + properties.passwordPepper

    private fun normalizeEmail(email: String): String = email.trim().lowercase(Locale.ROOT)

    private companion object {
        const val LOGIN_PER_USER_LIMIT = 10L
    }
}

data class TokenPair(
    val accessToken: String,
    val accessExpiresAt: java.time.Instant,
    val refreshToken: String,
    val refreshExpiresAt: java.time.Instant,
    val tokenType: String,
)
