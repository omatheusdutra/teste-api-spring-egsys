package br.com.egsys.tasks.infrastructure.persistence.adapter

import br.com.egsys.tasks.domain.model.Tarefa
import br.com.egsys.tasks.domain.model.TarefaId
import br.com.egsys.tasks.domain.model.UsuarioId
import br.com.egsys.tasks.domain.port.TarefaRepository
import br.com.egsys.tasks.infrastructure.persistence.mapper.DomainEventJpaMapper
import br.com.egsys.tasks.infrastructure.persistence.mapper.TarefaJpaMapper
import br.com.egsys.tasks.infrastructure.persistence.repository.SpringDataCategoriaRepository
import br.com.egsys.tasks.infrastructure.persistence.repository.SpringDataOutboxEventRepository
import br.com.egsys.tasks.infrastructure.persistence.repository.SpringDataTarefaHistoricoRepository
import br.com.egsys.tasks.infrastructure.persistence.repository.SpringDataTarefaRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

@Repository
@Transactional(readOnly = true)
class JpaTarefaRepository(
    private val repository: SpringDataTarefaRepository,
    private val categoriaRepository: SpringDataCategoriaRepository,
    private val outboxRepository: SpringDataOutboxEventRepository,
    private val historicoRepository: SpringDataTarefaHistoricoRepository,
    private val clock: Clock,
) : TarefaRepository {
    @Transactional
    override fun save(tarefa: Tarefa): Tarefa {
        val events = tarefa.pullDomainEvents()
        val categoria = categoriaRepository.getReferenceById(tarefa.categoria.id.value)
        val entity = TarefaJpaMapper.toEntity(tarefa, categoria)
        val saved = repository.saveAndFlush(entity)

        if (events.isNotEmpty()) {
            outboxRepository.saveAllAndFlush(
                events.map { DomainEventJpaMapper.toOutboxEntity(it, tarefa.ownerId, clock.instant()) },
            )
            historicoRepository.saveAllAndFlush(
                events.map { DomainEventJpaMapper.toHistoricoEntity(it, tarefa.ownerId) },
            )
        }

        return TarefaJpaMapper.toDomain(saved)
    }

    override fun findById(
        id: TarefaId,
        ownerId: UsuarioId,
    ): Tarefa? =
        repository
            .findByIdAndOwnerIdAndExcluidaEmIsNull(id.value, ownerId.value)
            ?.let(TarefaJpaMapper::toDomain)

    override fun findByIdIncludingDeleted(
        id: TarefaId,
        ownerId: UsuarioId,
    ): Tarefa? =
        repository
            .findByIdAndOwnerId(id.value, ownerId.value)
            ?.let(TarefaJpaMapper::toDomain)

    override fun findAllActive(ownerId: UsuarioId): List<Tarefa> =
        repository
            .findAllByOwnerIdAndExcluidaEmIsNullOrderByDataHoraAscIdAsc(ownerId.value)
            .map(TarefaJpaMapper::toDomain)
}
