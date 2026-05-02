package br.com.egsys.tasks.application.usecase

import br.com.egsys.tasks.application.exception.TarefaNaoEncontradaException
import br.com.egsys.tasks.domain.exception.InvalidTaskStateTransitionException
import br.com.egsys.tasks.domain.model.Tarefa
import br.com.egsys.tasks.domain.model.TarefaId
import br.com.egsys.tasks.domain.model.TarefaStatus
import br.com.egsys.tasks.domain.model.UsuarioId
import br.com.egsys.tasks.domain.port.TarefaRepository
import java.time.Clock
import java.util.UUID

data class AlterarStatusTarefaCommand(
    val id: UUID,
    val ownerId: UUID,
    val status: TarefaStatus,
)

class AlterarStatusTarefaUseCase(
    private val tarefas: TarefaRepository,
    private val clock: Clock,
) {
    fun execute(command: AlterarStatusTarefaCommand): Tarefa {
        val tarefaId = TarefaId.from(command.id)
        val ownerId = UsuarioId.from(command.ownerId)
        val tarefa = tarefas.findById(tarefaId, ownerId) ?: throw TarefaNaoEncontradaException(command.id)

        when (command.status) {
            TarefaStatus.PENDENTE -> throw InvalidTaskStateTransitionException("status PENDENTE e apenas inicial")
            TarefaStatus.EM_ANDAMENTO -> tarefa.iniciar(clock)
            TarefaStatus.CONCLUIDA -> tarefa.concluir(clock)
            TarefaStatus.CANCELADA -> tarefa.cancelar(clock)
        }

        return tarefas.save(tarefa)
    }
}
