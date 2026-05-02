package br.com.egsys.tasks.infrastructure.persistence.repository

import br.com.egsys.tasks.infrastructure.persistence.entity.TarefaJpaEntity
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

interface SpringDataTarefaRepository : JpaRepository<TarefaJpaEntity, UUID> {
    @EntityGraph(attributePaths = ["categoria"])
    override fun findById(id: UUID): Optional<TarefaJpaEntity>

    @EntityGraph(attributePaths = ["categoria"])
    fun findByIdAndOwnerIdAndExcluidaEmIsNull(
        id: UUID,
        ownerId: UUID,
    ): TarefaJpaEntity?

    @EntityGraph(attributePaths = ["categoria"])
    fun findByIdAndOwnerId(
        id: UUID,
        ownerId: UUID,
    ): TarefaJpaEntity?

    @EntityGraph(attributePaths = ["categoria"])
    fun findAllByOwnerIdAndExcluidaEmIsNullOrderByDataHoraAscIdAsc(ownerId: UUID): List<TarefaJpaEntity>
}
