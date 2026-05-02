package br.com.egsys.tasks.web.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant

@JsonIgnoreProperties(ignoreUnknown = false)
data class RegisterRequest(
    @field:Email
    @field:NotBlank
    @field:Size(max = 320)
    @field:Schema(example = "ana@example.com")
    val email: String?,
    @field:NotBlank
    @field:Size(min = 12, max = 128)
    @field:Schema(example = "Senha-forte-123!")
    val password: String?,
)

@JsonIgnoreProperties(ignoreUnknown = false)
data class LoginRequest(
    @field:Email
    @field:NotBlank
    @field:Size(max = 320)
    @field:Schema(example = "ana@example.com")
    val email: String?,
    @field:NotBlank
    @field:Size(max = 128)
    @field:Schema(example = "Senha-forte-123!")
    val password: String?,
)

@JsonIgnoreProperties(ignoreUnknown = false)
data class RefreshRequest(
    @field:NotBlank
    @field:Size(max = 512)
    val refreshToken: String?,
)

data class TokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String,
    val accessExpiresAt: Instant,
    val refreshExpiresAt: Instant,
)
