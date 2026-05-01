package br.com.egsys.tasks.infrastructure.persistence.adapter

import br.com.egsys.tasks.domain.model.Tarefa
import br.com.egsys.tasks.domain.model.TarefaId
import br.com.egsys.tasks.domain.port.TarefaRepository
import br.com.egsys.tasks.infrastructure.persistence.mapper.TarefaJpaMapper
import br.com.egsys.tasks.infrastructure.persistence.repository.SpringDataCategoriaRepository
import br.com.egsys.tasks.infrastructure.persistence.repository.SpringDataTarefaRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
@Transactional(readOnly = true)
class JpaTarefaRepository(
    private val repository: SpringDataTarefaRepository,
    private val categoriaRepository: SpringDataCategoriaRepository,
) : TarefaRepository {
    @Transactional
    override fun save(tarefa: Tarefa): Tarefa {
        val categoria = categoriaRepository.getReferenceById(tarefa.categoria.id.value)
        val entity = TarefaJpaMapper.toEntity(tarefa, categoria)

        return TarefaJpaMapper.toDomain(repository.saveAndFlush(entity))
    }

    override fun findById(id: TarefaId): Tarefa? =
        repository
            .findByIdAndExcluidaEmIsNull(id.value)
            ?.let(TarefaJpaMapper::toDomain)

    override fun findByIdIncludingDeleted(id: TarefaId): Tarefa? =
        repository
            .findById(id.value)
            .map(TarefaJpaMapper::toDomain)
            .orElse(null)

    override fun findAllActive(): List<Tarefa> =
        repository
            .findAllByExcluidaEmIsNullOrderByDataHoraAscIdAsc()
            .map(TarefaJpaMapper::toDomain)
}
