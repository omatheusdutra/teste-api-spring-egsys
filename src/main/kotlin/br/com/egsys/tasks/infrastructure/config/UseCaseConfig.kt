package br.com.egsys.tasks.infrastructure.config

import br.com.egsys.tasks.application.usecase.AtualizarTarefaUseCase
import br.com.egsys.tasks.application.usecase.BuscarTarefaUseCase
import br.com.egsys.tasks.application.usecase.CriarTarefaUseCase
import br.com.egsys.tasks.application.usecase.ExcluirTarefaUseCase
import br.com.egsys.tasks.application.usecase.ListarCategoriasUseCase
import br.com.egsys.tasks.application.usecase.ListarTarefasUseCase
import br.com.egsys.tasks.domain.port.CategoriaRepository
import br.com.egsys.tasks.domain.port.TarefaRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration
class UseCaseConfig {
    @Bean
    fun clock(): Clock = Clock.systemUTC()

    @Bean
    fun listarCategorias(categorias: CategoriaRepository): ListarCategoriasUseCase = ListarCategoriasUseCase(categorias)

    @Bean
    fun criarTarefaUseCase(
        tarefas: TarefaRepository,
        categorias: CategoriaRepository,
        clock: Clock,
    ): CriarTarefaUseCase = CriarTarefaUseCase(tarefas, categorias, clock)

    @Bean
    fun atualizarTarefaUseCase(
        tarefas: TarefaRepository,
        categorias: CategoriaRepository,
        clock: Clock,
    ): AtualizarTarefaUseCase = AtualizarTarefaUseCase(tarefas, categorias, clock)

    @Bean
    fun buscarTarefa(tarefas: TarefaRepository): BuscarTarefaUseCase = BuscarTarefaUseCase(tarefas)

    @Bean
    fun listarTarefas(tarefas: TarefaRepository): ListarTarefasUseCase = ListarTarefasUseCase(tarefas)

    @Bean
    fun excluirTarefaUseCase(
        tarefas: TarefaRepository,
        clock: Clock,
    ): ExcluirTarefaUseCase = ExcluirTarefaUseCase(tarefas, clock)
}
