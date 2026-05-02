package br.com.egsys.tasks.application.usecase

import br.com.egsys.tasks.application.exception.TarefaNaoEncontradaException
import br.com.egsys.tasks.domain.model.Tarefa
import br.com.egsys.tasks.domain.model.TarefaId
import br.com.egsys.tasks.domain.model.UsuarioId
import br.com.egsys.tasks.domain.port.TarefaRepository
import java.util.UUID

class BuscarTarefaUseCase(
    private val tarefas: TarefaRepository,
) {
    fun execute(
        id: UUID,
        ownerId: UUID,
    ): Tarefa {
        val tarefaId = TarefaId.from(id)
        return tarefas.findById(tarefaId, UsuarioId.from(ownerId)) ?: throw TarefaNaoEncontradaException(id)
    }
}
