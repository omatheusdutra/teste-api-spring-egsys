package br.com.egsys.tasks.domain.port

import br.com.egsys.tasks.domain.model.TarefaHistorico
import br.com.egsys.tasks.domain.model.TarefaId
import br.com.egsys.tasks.domain.model.UsuarioId

interface TarefaHistoricoRepository {
    fun findByTarefaId(
        tarefaId: TarefaId,
        ownerId: UsuarioId,
    ): List<TarefaHistorico>
}
