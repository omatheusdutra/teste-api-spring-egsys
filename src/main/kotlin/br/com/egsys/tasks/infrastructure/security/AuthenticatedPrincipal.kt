package br.com.egsys.tasks.infrastructure.security

import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import java.util.UUID

data class AuthenticatedPrincipal(
    val userId: UUID,
    val email: String,
    val role: UserRole,
) {
    val authorities: List<GrantedAuthority> = listOf(SimpleGrantedAuthority(role.name))
}
