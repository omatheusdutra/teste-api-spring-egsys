package br.com.egsys.tasks.infrastructure.persistence.repository

import br.com.egsys.tasks.infrastructure.persistence.entity.CategoriaJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SpringDataCategoriaRepository : JpaRepository<CategoriaJpaEntity, UUID> {
    fun findAllByOrderByDescricaoAsc(): List<CategoriaJpaEntity>
}
