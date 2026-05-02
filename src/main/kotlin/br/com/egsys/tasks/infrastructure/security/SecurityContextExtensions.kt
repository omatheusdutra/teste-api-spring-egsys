package br.com.egsys.tasks.infrastructure.security

import org.springframework.security.core.Authentication
import java.util.UUID

fun Authentication.currentUserId(): UUID =
    when (val authenticated = principal) {
        is AuthenticatedPrincipal -> authenticated.userId
        else -> UUID.fromString(name)
    }

fun Authentication.currentPrincipal(): AuthenticatedPrincipal = principal as AuthenticatedPrincipal
