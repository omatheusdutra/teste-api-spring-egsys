package br.com.egsys.tasks.security

import br.com.egsys.tasks.infrastructure.security.AuthenticatedPrincipal
import br.com.egsys.tasks.infrastructure.security.InvalidTokenException
import br.com.egsys.tasks.infrastructure.security.JwtKeyProvider
import br.com.egsys.tasks.infrastructure.security.JwtProperties
import br.com.egsys.tasks.infrastructure.security.JwtService
import br.com.egsys.tasks.infrastructure.security.SecurityProperties
import br.com.egsys.tasks.infrastructure.security.UserRole
import com.fasterxml.jackson.databind.ObjectMapper
import io.jsonwebtoken.Jwts
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.mock.env.MockEnvironment
import java.security.KeyPairGenerator
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import java.util.Date
import java.util.UUID

class AuthenticationTests {
    private val now = Instant.parse("2026-05-01T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val revokedTokens = InMemoryRevokedTokenStore()
    private val properties =
        SecurityProperties(
            jwt =
                JwtProperties(
                    issuer = "egsys-test",
                    audience = "egsys-clients",
                    accessTokenTtl = Duration.ofMinutes(15),
                ),
        )
    private val keys = JwtKeyProvider(properties, MockEnvironment().withProperty("spring.profiles.active", "test"))
    private val jwt = JwtService(properties, keys, revokedTokens, ObjectMapper(), clock)
    private val userId = UUID.fromString("018f95df-0c7b-7af2-a199-447f82f36914")
    private val principal = AuthenticatedPrincipal(userId, "ana@example.com", UserRole.ROLE_USER)

    @Test
    fun `deve aceitar token RS256 valido`() {
        val token = jwt.issueAccessToken(principal)

        jwt.authenticate(token.token).principal.userId shouldBe userId
    }

    @Test
    fun `deve rejeitar token com alg none`() {
        shouldThrow<InvalidTokenException> {
            jwt.authenticate(unsignedToken("none"))
        }
    }

    @Test
    fun `deve rejeitar token com alg HS256`() {
        shouldThrow<InvalidTokenException> {
            jwt.authenticate(unsignedToken("HS256"))
        }
    }

    @Test
    fun `deve rejeitar token expirado`() {
        val expired = signedToken(expiresAt = now.minusSeconds(1))

        shouldThrow<InvalidTokenException> {
            jwt.authenticate(expired)
        }
    }

    @Test
    fun `deve rejeitar token com assinatura adulterada`() {
        val issued = jwt.issueAccessToken(principal).token
        val tampered = issued.dropLast(2) + "xx"

        shouldThrow<InvalidTokenException> {
            jwt.authenticate(tampered)
        }
    }

    @Test
    fun `deve rejeitar token com iss ou aud incorretos`() {
        shouldThrow<InvalidTokenException> {
            jwt.authenticate(signedToken(issuer = "evil"))
        }

        shouldThrow<InvalidTokenException> {
            jwt.authenticate(signedToken(audience = "evil"))
        }
    }

    @Test
    fun `deve rejeitar JTI revogado`() {
        val issued = jwt.issueAccessToken(principal)
        revokedTokens.revoke(issued.jti, Duration.ofMinutes(15))

        shouldThrow<InvalidTokenException> {
            jwt.authenticate(issued.token)
        }
    }

    @Test
    fun `deve carregar chaves RSA PEM com quebras escapadas por env`() {
        val pair =
            KeyPairGenerator
                .getInstance("RSA")
                .apply { initialize(2048) }
                .generateKeyPair()
        val configured =
            SecurityProperties(
                jwt =
                    JwtProperties(
                        privateKey = pem("PRIVATE KEY", pair.private.encoded).replace("\n", "\\n"),
                        publicKey = pem("PUBLIC KEY", pair.public.encoded).replace("\n", "\\n"),
                    ),
            )
        val provider = JwtKeyProvider(configured, MockEnvironment().withProperty("spring.profiles.active", "prod"))

        provider.privateKey().encoded shouldBe pair.private.encoded
        provider.publicKey().encoded shouldBe pair.public.encoded
    }

    private fun signedToken(
        issuer: String = properties.jwt.issuer,
        audience: String = properties.jwt.audience,
        expiresAt: Instant = now.plusSeconds(900),
    ): String =
        Jwts
            .builder()
            .issuer(issuer)
            .audience()
            .add(audience)
            .and()
            .subject(userId.toString())
            .id(UUID.randomUUID().toString())
            .issuedAt(Date.from(now))
            .notBefore(Date.from(now))
            .expiration(Date.from(expiresAt))
            .claim("roles", listOf(UserRole.ROLE_USER.name))
            .signWith(keys.privateKey(), Jwts.SIG.RS256)
            .compact()

    private fun unsignedToken(alg: String): String {
        val header = base64("""{"alg":"$alg","typ":"JWT"}""")
        val payload = base64("""{"sub":"$userId","iss":"egsys-test","aud":["egsys-clients"],"jti":"abc"}""")
        return "$header.$payload."
    }

    private fun base64(value: String): String {
        val bytes = value.toByteArray()
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun pem(
        label: String,
        bytes: ByteArray,
    ): String {
        val encoded = Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(bytes)
        return "-----BEGIN $label-----\n$encoded\n-----END $label-----"
    }
}
