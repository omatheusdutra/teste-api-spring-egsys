package br.com.egsys.tasks.application.usecase

import br.com.egsys.tasks.application.exception.CategoriaNaoEncontradaException
import br.com.egsys.tasks.application.exception.TarefaNaoEncontradaException
import br.com.egsys.tasks.domain.model.CategoriaId
import br.com.egsys.tasks.domain.model.DataHoraTarefa
import br.com.egsys.tasks.domain.model.Descricao
import br.com.egsys.tasks.domain.model.Tarefa
import br.com.egsys.tasks.domain.model.TarefaId
import br.com.egsys.tasks.domain.model.Titulo
import br.com.egsys.tasks.domain.model.UsuarioId
import br.com.egsys.tasks.domain.port.CategoriaRepository
import br.com.egsys.tasks.domain.port.TarefaRepository
import java.time.Clock

class AtualizarTarefaUseCase(
    private val tarefas: TarefaRepository,
    private val categorias: CategoriaRepository,
    private val clock: Clock,
) {
    fun execute(command: AtualizarTarefaCommand): Tarefa {
        val tarefaId = TarefaId.from(command.id)
        val ownerId = UsuarioId.from(command.ownerId)
        val categoriaId = CategoriaId.from(command.categoriaId)
        val tarefa = tarefas.findById(tarefaId, ownerId) ?: throw TarefaNaoEncontradaException(command.id)
        val categoria = categorias.findById(categoriaId) ?: throw CategoriaNaoEncontradaException(command.categoriaId)

        tarefa.atualizar(
            titulo = Titulo.of(command.titulo),
            descricao = Descricao.of(command.descricao),
            categoria = categoria,
            dataHora = DataHoraTarefa.agendadaPara(command.dataHora, clock),
            clock = clock,
        )

        return tarefas.save(tarefa)
    }
}
