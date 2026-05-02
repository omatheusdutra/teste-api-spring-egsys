package br.com.egsys.tasks.domain.port

import br.com.egsys.tasks.domain.model.Tarefa
import br.com.egsys.tasks.domain.model.TarefaId
import br.com.egsys.tasks.domain.model.UsuarioId

interface TarefaRepository {
    fun save(tarefa: Tarefa): Tarefa

    fun findById(
        id: TarefaId,
        ownerId: UsuarioId,
    ): Tarefa?

    fun findByIdIncludingDeleted(
        id: TarefaId,
        ownerId: UsuarioId,
    ): Tarefa?

    fun findAllActive(ownerId: UsuarioId): List<Tarefa>
}
