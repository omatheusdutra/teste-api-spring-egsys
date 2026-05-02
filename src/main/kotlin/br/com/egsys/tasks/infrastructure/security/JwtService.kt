package br.com.egsys.tasks.infrastructure.security

import com.fasterxml.jackson.databind.ObjectMapper
import io.jsonwebtoken.Jwts
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.Date
import java.util.UUID

@Service
class JwtService(
    private val properties: SecurityProperties,
    private val keys: JwtKeyProvider,
    private val revokedTokens: RevokedTokenStore,
    private val objectMapper: ObjectMapper,
    private val clock: Clock,
) {
    fun issueAccessToken(user: AuthenticatedPrincipal): IssuedAccessToken {
        val now = clock.instant()
        val expiresAt = now.plus(properties.jwt.accessTokenTtl)
        val jti = UUID.randomUUID().toString()
        val token =
            Jwts
                .builder()
                .issuer(properties.jwt.issuer)
                .audience()
                .add(properties.jwt.audience)
                .and()
                .subject(user.userId.toString())
                .id(jti)
                .issuedAt(Date.from(now))
                .notBefore(Date.from(now))
                .expiration(Date.from(expiresAt))
                .claim("roles", listOf(user.role.name))
                .claim("email", user.email)
                .signWith(keys.privateKey(), Jwts.SIG.RS256)
                .compact()

        return IssuedAccessToken(token = token, jti = jti, expiresAt = expiresAt)
    }

    @Suppress("ThrowsCount")
    fun authenticate(token: String): AuthenticatedJwt {
        rejectUnsafeAlgorithm(token)

        val claims =
            runCatching {
                Jwts
                    .parser()
                    .verifyWith(keys.publicKey())
                    .clock { Date.from(clock.instant()) }
                    .requireIssuer(properties.jwt.issuer)
                    .requireAudience(properties.jwt.audience)
                    .build()
                    .parseSignedClaims(token)
                    .payload
            }.getOrElse {
                throw InvalidTokenException()
            }

        val jti = claims.id ?: throw InvalidTokenException()
        if (revokedTokens.isRevoked(jti)) {
            throw InvalidTokenException("token revogado")
        }

        val role =
            claims["roles"]
                ?.let { it as? List<*> }
                ?.firstOrNull()
                ?.toString()
                ?.let(UserRole::valueOf)
                ?: throw InvalidTokenException()

        return AuthenticatedJwt(
            principal =
                AuthenticatedPrincipal(
                    userId = UUID.fromString(claims.subject),
                    email = claims["email"]?.toString().orEmpty(),
                    role = role,
                ),
            credentials =
                JwtCredentials(
                    jti = jti,
                    expiresAt = claims.expiration.toInstant(),
                ),
        )
    }

    fun revoke(
        jti: String,
        expiresAt: Instant,
    ) {
        val ttl = Duration.between(clock.instant(), expiresAt).coerceAtLeast(Duration.ZERO)
        revokedTokens.revoke(jti, ttl)
    }

    fun accessTokenTtl(): Duration = properties.jwt.accessTokenTtl

    @Suppress("ThrowsCount")
    private fun rejectUnsafeAlgorithm(token: String) {
        val header =
            runCatching {
                val encodedHeader = token.substringBefore(".")
                objectMapper.readTree(String(Base64.getUrlDecoder().decode(encodedHeader), Charsets.UTF_8))
            }.getOrElse {
                throw InvalidTokenException()
            }
        val alg = header["alg"]?.asText() ?: throw InvalidTokenException()

        if (alg != "RS256") {
            throw InvalidTokenException("algoritmo JWT nao permitido")
        }
    }
}

data class IssuedAccessToken(
    val token: String,
    val jti: String,
    val expiresAt: Instant,
)

data class AuthenticatedJwt(
    val principal: AuthenticatedPrincipal,
    val credentials: JwtCredentials,
)
