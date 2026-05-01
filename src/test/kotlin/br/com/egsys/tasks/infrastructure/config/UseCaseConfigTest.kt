package br.com.egsys.tasks.infrastructure.config

import br.com.egsys.tasks.application.usecase.AtualizarTarefaUseCase
import br.com.egsys.tasks.application.usecase.BuscarTarefaUseCase
import br.com.egsys.tasks.application.usecase.CriarTarefaUseCase
import br.com.egsys.tasks.application.usecase.ExcluirTarefaUseCase
import br.com.egsys.tasks.application.usecase.ListarCategoriasUseCase
import br.com.egsys.tasks.application.usecase.ListarTarefasUseCase
import br.com.egsys.tasks.domain.port.CategoriaRepository
import br.com.egsys.tasks.domain.port.TarefaRepository
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.time.Clock

class UseCaseConfigTest {
    private val tarefas = mockk<TarefaRepository>()
    private val categorias = mockk<CategoriaRepository>()
    private val config = UseCaseConfig()

    @Test
    fun `wires application use cases`() {
        val clock = config.clock()

        clock.shouldBeInstanceOf<Clock>()
        config.listarCategorias(categorias).shouldBeInstanceOf<ListarCategoriasUseCase>()
        config.criarTarefaUseCase(tarefas, categorias, clock).shouldBeInstanceOf<CriarTarefaUseCase>()
        config.atualizarTarefaUseCase(tarefas, categorias, clock).shouldBeInstanceOf<AtualizarTarefaUseCase>()
        config.buscarTarefa(tarefas).shouldBeInstanceOf<BuscarTarefaUseCase>()
        config.listarTarefas(tarefas).shouldBeInstanceOf<ListarTarefasUseCase>()
        config.excluirTarefaUseCase(tarefas, clock).shouldBeInstanceOf<ExcluirTarefaUseCase>()
    }

    @Test
    fun `uses utc system clock`() {
        config.clock().zone shouldBe Clock.systemUTC().zone
    }
}
