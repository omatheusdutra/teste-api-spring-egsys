package br.com.egsys.tasks.web.controller

import br.com.egsys.tasks.infrastructure.security.AuthService
import br.com.egsys.tasks.infrastructure.security.JwtCredentials
import br.com.egsys.tasks.web.dto.LoginRequest
import br.com.egsys.tasks.web.dto.RefreshRequest
import br.com.egsys.tasks.web.dto.RegisterRequest
import br.com.egsys.tasks.web.dto.TokenResponse
import br.com.egsys.tasks.web.mapper.toResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/auth", produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(name = "Autenticacao")
class AuthController(
    private val auth: AuthService,
) {
    @PostMapping("/register", consumes = [MediaType.APPLICATION_JSON_VALUE])
    @PreAuthorize("permitAll()")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cadastra usuario e emite tokens")
    fun register(
        @Valid @RequestBody request: RegisterRequest,
    ): TokenResponse =
        auth
            .register(email = requireNotNull(request.email), password = requireNotNull(request.password))
            .toResponse()

    @PostMapping("/login", consumes = [MediaType.APPLICATION_JSON_VALUE])
    @PreAuthorize("permitAll()")
    @Operation(summary = "Autentica usuario e emite tokens")
    fun login(
        @Valid @RequestBody request: LoginRequest,
    ): TokenResponse =
        auth
            .login(email = requireNotNull(request.email), password = requireNotNull(request.password))
            .toResponse()

    @PostMapping("/refresh", consumes = [MediaType.APPLICATION_JSON_VALUE])
    @PreAuthorize("permitAll()")
    @Operation(summary = "Rotaciona refresh token")
    fun refresh(
        @Valid @RequestBody request: RefreshRequest,
    ): TokenResponse = auth.refresh(requireNotNull(request.refreshToken)).toResponse()

    @PostMapping("/logout")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Revoga o access token atual")
    fun logout(authentication: Authentication) {
        val credentials = authentication.credentials as JwtCredentials
        auth.logout(jti = credentials.jti, expiresAt = credentials.expiresAt)
    }

    @PostMapping("/revoke-all/{userId:[0-9a-fA-F\\-]{36}}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Revoga todos os refresh tokens de um usuario")
    fun revokeAll(
        @PathVariable userId: UUID,
    ) {
        auth.revokeAll(userId)
    }
}
