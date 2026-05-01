package br.com.egsys.tasks.application.usecase

import br.com.egsys.tasks.application.exception.CategoriaNaoEncontradaException
import br.com.egsys.tasks.domain.model.CategoriaId
import br.com.egsys.tasks.domain.model.DataHoraTarefa
import br.com.egsys.tasks.domain.model.Descricao
import br.com.egsys.tasks.domain.model.Tarefa
import br.com.egsys.tasks.domain.model.TarefaId
import br.com.egsys.tasks.domain.model.Titulo
import br.com.egsys.tasks.domain.port.CategoriaRepository
import br.com.egsys.tasks.domain.port.TarefaRepository
import java.time.Clock

class CriarTarefaUseCase(
    private val tarefas: TarefaRepository,
    private val categorias: CategoriaRepository,
    private val clock: Clock,
    private val idGenerator: () -> TarefaId = TarefaId::new,
) {
    fun execute(command: CriarTarefaCommand): Tarefa {
        val categoriaId = CategoriaId.from(command.categoriaId)
        val categoria = categorias.findById(categoriaId) ?: throw CategoriaNaoEncontradaException(command.categoriaId)
        val tarefa =
            Tarefa.criar(
                id = idGenerator(),
                titulo = Titulo.of(command.titulo),
                descricao = Descricao.of(command.descricao),
                categoria = categoria,
                dataHora = DataHoraTarefa.agendadaPara(command.dataHora, clock),
                clock = clock,
            )

        return tarefas.save(tarefa)
    }
}
