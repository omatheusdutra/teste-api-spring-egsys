package br.com.egsys.tasks.domain.port

import br.com.egsys.tasks.domain.model.Tarefa
import br.com.egsys.tasks.domain.model.TarefaId

interface TarefaRepository {
    fun save(tarefa: Tarefa): Tarefa

    fun findById(id: TarefaId): Tarefa?

    fun findByIdIncludingDeleted(id: TarefaId): Tarefa?

    fun findAllActive(): List<Tarefa>
}
