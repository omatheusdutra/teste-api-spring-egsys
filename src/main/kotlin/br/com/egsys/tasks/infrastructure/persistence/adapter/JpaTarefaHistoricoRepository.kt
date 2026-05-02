package br.com.egsys.tasks.infrastructure.persistence.adapter

import br.com.egsys.tasks.domain.model.TarefaHistorico
import br.com.egsys.tasks.domain.model.TarefaId
import br.com.egsys.tasks.domain.model.UsuarioId
import br.com.egsys.tasks.domain.port.TarefaHistoricoRepository
import br.com.egsys.tasks.infrastructure.persistence.mapper.TarefaHistoricoJpaMapper
import br.com.egsys.tasks.infrastructure.persistence.repository.SpringDataTarefaHistoricoRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
@Transactional(readOnly = true)
class JpaTarefaHistoricoRepository(
    private val repository: SpringDataTarefaHistoricoRepository,
) : TarefaHistoricoRepository {
    override fun findByTarefaId(
        tarefaId: TarefaId,
        ownerId: UsuarioId,
    ): List<TarefaHistorico> =
        repository
            .findAllByTarefaIdAndOwnerIdOrderByOccurredAtAscIdAsc(tarefaId.value, ownerId.value)
            .map(TarefaHistoricoJpaMapper::toDomain)
}
