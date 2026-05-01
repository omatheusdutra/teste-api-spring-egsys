package br.com.egsys.tasks.application.usecase

import br.com.egsys.tasks.application.exception.TarefaNaoEncontradaException
import br.com.egsys.tasks.domain.model.Tarefa
import br.com.egsys.tasks.domain.model.TarefaId
import br.com.egsys.tasks.domain.port.TarefaRepository
import java.time.Clock
import java.util.UUID

class ExcluirTarefaUseCase(
    private val tarefas: TarefaRepository,
    private val clock: Clock,
) {
    fun execute(id: UUID): Tarefa {
        val tarefaId = TarefaId.from(id)
        val tarefa = tarefas.findById(tarefaId) ?: throw TarefaNaoEncontradaException(id)

        tarefa.excluir(clock)

        return tarefas.save(tarefa)
    }
}
