package br.com.egsys.tasks.application.usecase

import br.com.egsys.tasks.application.exception.TarefaNaoEncontradaException
import br.com.egsys.tasks.domain.model.TarefaHistorico
import br.com.egsys.tasks.domain.model.TarefaId
import br.com.egsys.tasks.domain.model.UsuarioId
import br.com.egsys.tasks.domain.port.TarefaHistoricoRepository
import br.com.egsys.tasks.domain.port.TarefaRepository
import java.util.UUID

class ListarHistoricoTarefaUseCase(
    private val tarefas: TarefaRepository,
    private val historico: TarefaHistoricoRepository,
) {
    fun execute(
        id: UUID,
        ownerId: UUID,
    ): List<TarefaHistorico> {
        val tarefaId = TarefaId.from(id)
        val usuarioId = UsuarioId.from(ownerId)
        tarefas.findByIdIncludingDeleted(tarefaId, usuarioId) ?: throw TarefaNaoEncontradaException(id)

        return historico.findByTarefaId(tarefaId, usuarioId)
    }
}
