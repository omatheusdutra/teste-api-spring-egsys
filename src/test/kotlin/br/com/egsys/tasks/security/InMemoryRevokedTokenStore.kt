package br.com.egsys.tasks.security

import br.com.egsys.tasks.infrastructure.security.RevokedTokenStore
import java.time.Duration

class InMemoryRevokedTokenStore : RevokedTokenStore {
    private val revoked = mutableSetOf<String>()

    override fun revoke(
        jti: String,
        ttl: Duration,
    ) {
        revoked += jti
    }

    override fun isRevoked(jti: String): Boolean = jti in revoked
}
