package br.com.egsys.tasks.infrastructure.security

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

interface RevokedTokenStore {
    fun revoke(
        jti: String,
        ttl: Duration,
    )

    fun isRevoked(jti: String): Boolean
}

@Component
class RedisRevokedTokenStore(
    private val redis: StringRedisTemplate,
) : RevokedTokenStore {
    override fun revoke(
        jti: String,
        ttl: Duration,
    ) {
        redis.opsForValue().set(key(jti), "revoked", ttl)
    }

    override fun isRevoked(jti: String): Boolean = redis.hasKey(key(jti)) == true

    private fun key(jti: String): String = "jwt:revoked:$jti"
}
