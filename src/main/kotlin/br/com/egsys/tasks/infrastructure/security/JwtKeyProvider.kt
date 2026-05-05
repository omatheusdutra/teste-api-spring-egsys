package br.com.egsys.tasks.infrastructure.security

import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

@Component
class JwtKeyProvider(
    private val properties: SecurityProperties,
    private val environment: Environment,
) {
    private val generated: KeyPair by lazy {
        KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    }

    fun privateKey(): PrivateKey {
        val configured = properties.jwt.privateKey.trim()
        if (configured.isBlank()) {
            ensureNonProdProfile()
            return generated.private
        }

        return KeyFactory
            .getInstance("RSA")
            .generatePrivate(PKCS8EncodedKeySpec(decodePem(configured)))
    }

    fun publicKey(): PublicKey {
        val configured = properties.jwt.publicKey.trim()
        if (configured.isBlank()) {
            ensureNonProdProfile()
            return generated.public
        }

        return KeyFactory
            .getInstance("RSA")
            .generatePublic(X509EncodedKeySpec(decodePem(configured)))
    }

    private fun ensureNonProdProfile() {
        if (environment.activeProfiles.any { it == "prod" }) {
            error("JWT RSA keys must be configured externally in prod")
        }
    }

    private fun decodePem(value: String): ByteArray {
        val normalized = value.replace("\\n", "\n")
        val sanitized =
            normalized
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replace("\\s".toRegex(), "")

        return Base64.getDecoder().decode(sanitized)
    }
}
