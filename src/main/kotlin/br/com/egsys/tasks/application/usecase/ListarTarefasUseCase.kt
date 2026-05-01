package br.com.egsys.tasks.application.usecase

import br.com.egsys.tasks.domain.model.Tarefa
import br.com.egsys.tasks.domain.port.TarefaRepository

class ListarTarefasUseCase(
    private val tarefas: TarefaRepository,
) {
    fun execute(): List<Tarefa> = tarefas.findAllActive()
}
