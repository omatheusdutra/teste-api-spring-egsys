package br.com.egsys.tasks.infrastructure.persistence.repository

import br.com.egsys.tasks.infrastructure.persistence.entity.TarefaHistoricoJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SpringDataTarefaHistoricoRepository : JpaRepository<TarefaHistoricoJpaEntity, UUID> {
    fun findAllByTarefaIdAndOwnerIdOrderByOccurredAtAscIdAsc(
        tarefaId: UUID,
        ownerId: UUID,
    ): List<TarefaHistoricoJpaEntity>
}
