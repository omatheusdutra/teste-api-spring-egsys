package br.com.egsys.tasks.infrastructure.security

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "egsys.security")
data class SecurityProperties(
    var allowedOrigins: List<String> = emptyList(),
    var passwordPepper: String = "",
    var jwt: JwtProperties = JwtProperties(),
)

data class JwtProperties(
    var issuer: String = "egsys-tasks-api",
    var audience: String = "egsys-tasks-clients",
    var accessTokenTtl: Duration = Duration.ofMinutes(15),
    var refreshTokenTtl: Duration = Duration.ofDays(7),
    var privateKey: String = "",
    var publicKey: String = "",
)
