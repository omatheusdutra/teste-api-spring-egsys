package br.com.egsys.tasks.web.mapper

import br.com.egsys.tasks.infrastructure.security.TokenPair
import br.com.egsys.tasks.web.dto.TokenResponse

fun TokenPair.toResponse(): TokenResponse =
    TokenResponse(
        accessToken = accessToken,
        refreshToken = refreshToken,
        tokenType = tokenType,
        accessExpiresAt = accessExpiresAt,
        refreshExpiresAt = refreshExpiresAt,
    )
