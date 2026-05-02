package br.com.egsys.tasks.application.usecase

import br.com.egsys.tasks.domain.model.Tarefa
import br.com.egsys.tasks.domain.model.UsuarioId
import br.com.egsys.tasks.domain.port.TarefaRepository
import java.util.UUID

class ListarTarefasUseCase(
    private val tarefas: TarefaRepository,
) {
    fun execute(ownerId: UUID): List<Tarefa> = tarefas.findAllActive(UsuarioId.from(ownerId))
}
