package br.com.egsys.tasks.infrastructure.security.repository

import br.com.egsys.tasks.infrastructure.security.entity.UsuarioJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SpringDataUsuarioRepository : JpaRepository<UsuarioJpaEntity, UUID> {
    fun findByEmailIgnoreCase(email: String): UsuarioJpaEntity?

    fun existsByEmailIgnoreCase(email: String): Boolean
}
