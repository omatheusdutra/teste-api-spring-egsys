package br.com.egsys.tasks.infrastructure.security

import java.time.Instant

data class JwtCredentials(
    val jti: String,
    val expiresAt: Instant,
)
