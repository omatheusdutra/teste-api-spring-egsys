package br.com.egsys.tasks.infrastructure.security.entity

import br.com.egsys.tasks.infrastructure.security.UserRole
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "usuarios")
class UsuarioJpaEntity(
    @Id
    @Column(name = "id", nullable = false, columnDefinition = "uuid")
    var id: UUID? = null,
    @Column(name = "email", nullable = false, unique = true, length = 320)
    var email: String = "",
    @Column(name = "password_hash", nullable = false)
    var passwordHash: String = "",
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 40)
    var role: UserRole = UserRole.ROLE_USER,
    @Column(name = "criada_em", nullable = false, updatable = false)
    var criadaEm: Instant = Instant.EPOCH,
    @Column(name = "atualizada_em", nullable = false)
    var atualizadaEm: Instant = Instant.EPOCH,
)
