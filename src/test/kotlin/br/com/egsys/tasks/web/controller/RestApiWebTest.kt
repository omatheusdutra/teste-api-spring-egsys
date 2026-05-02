package br.com.egsys.tasks.web.controller

import br.com.egsys.tasks.application.exception.TarefaNaoEncontradaException
import br.com.egsys.tasks.application.usecase.AlterarStatusTarefaCommand
import br.com.egsys.tasks.application.usecase.AlterarStatusTarefaUseCase
import br.com.egsys.tasks.application.usecase.AtualizarTarefaCommand
import br.com.egsys.tasks.application.usecase.AtualizarTarefaUseCase
import br.com.egsys.tasks.application.usecase.BuscarTarefaUseCase
import br.com.egsys.tasks.application.usecase.CriarTarefaCommand
import br.com.egsys.tasks.application.usecase.CriarTarefaUseCase
import br.com.egsys.tasks.application.usecase.ExcluirTarefaUseCase
import br.com.egsys.tasks.application.usecase.ListarCategoriasUseCase
import br.com.egsys.tasks.application.usecase.ListarHistoricoTarefaUseCase
import br.com.egsys.tasks.application.usecase.ListarTarefasUseCase
import br.com.egsys.tasks.domain.model.Categoria
import br.com.egsys.tasks.domain.model.CategoriaId
import br.com.egsys.tasks.domain.model.DataHoraTarefa
import br.com.egsys.tasks.domain.model.Descricao
import br.com.egsys.tasks.domain.model.DescricaoCategoria
import br.com.egsys.tasks.domain.model.Tarefa
import br.com.egsys.tasks.domain.model.TarefaHistorico
import br.com.egsys.tasks.domain.model.TarefaId
import br.com.egsys.tasks.domain.model.TarefaStatus
import br.com.egsys.tasks.domain.model.Titulo
import br.com.egsys.tasks.domain.model.UsuarioId
import br.com.egsys.tasks.infrastructure.security.JwtService
import br.com.egsys.tasks.infrastructure.security.RateLimiterService
import br.com.egsys.tasks.web.exception.ApiExceptionHandler
import br.com.egsys.tasks.web.mapper.CursorCodec
import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.client.MockMvcWebTestClient
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.UUID

@WebMvcTest(controllers = [CategoriaController::class, TarefaController::class])
@AutoConfigureMockMvc(addFilters = false)
@Import(ApiExceptionHandler::class)
@WithMockUser(username = "018f95df-0c7b-7af2-a199-447f82f36914", roles = ["USER"])
@Suppress("UnusedPrivateProperty")
class RestApiWebTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockkBean
    private lateinit var listarCategorias: ListarCategoriasUseCase

    @MockkBean
    private lateinit var criarTarefa: CriarTarefaUseCase

    @MockkBean
    private lateinit var atualizarTarefa: AtualizarTarefaUseCase

    @MockkBean
    private lateinit var buscarTarefa: BuscarTarefaUseCase

    @MockkBean
    private lateinit var listarTarefas: ListarTarefasUseCase

    @MockkBean
    private lateinit var excluirTarefa: ExcluirTarefaUseCase

    @MockkBean
    private lateinit var alterarStatusTarefa: AlterarStatusTarefaUseCase

    @MockkBean
    private lateinit var listarHistoricoTarefa: ListarHistoricoTarefaUseCase

    @MockkBean
    private lateinit var jwtService: JwtService

    @MockkBean
    private lateinit var rateLimiter: RateLimiterService

    private lateinit var webTestClient: WebTestClient

    @BeforeEach
    fun setUp() {
        webTestClient = MockMvcWebTestClient.bindTo(mockMvc).build()
    }

    @Test
    fun `lists categories with MockMvc and WebTestClient`() {
        every { listarCategorias.execute() } returns listOf(casa, trabalho)

        mockMvc
            .perform(get("/api/v1/categorias"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].descricao").value("Casa"))
            .andExpect(jsonPath("$[1].descricao").value("Trabalho"))

        webTestClient
            .get()
            .uri("/api/v1/categorias")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$[0].id")
            .isEqualTo(casa.id.value.toString())

        verify(exactly = 2) { listarCategorias.execute() }
    }

    @Test
    fun `creates task and returns location`() {
        val command = slot<CriarTarefaCommand>()
        every { criarTarefa.execute(capture(command)) } returns tarefa()

        mockMvc
            .perform(
                post("/api/v1/tarefas")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(validTaskPayload()),
            ).andExpect(status().isCreated)
            .andExpect(header().string("Location", "http://localhost/api/v1/tarefas/$taskUuid"))
            .andExpect(jsonPath("$.id").value(taskUuid.toString()))
            .andExpect(jsonPath("$.titulo").value("Pagar aluguel"))

        command.captured.titulo shouldBe "Pagar aluguel"
        command.captured.ownerId shouldBe ownerUuid
        command.captured.categoriaId shouldBe casa.id.value
        command.captured.dataHora shouldBe futureDate
    }

    @Test
    fun `gets task by id with WebTestClient`() {
        every { buscarTarefa.execute(taskUuid, ownerUuid) } returns tarefa()

        webTestClient
            .get()
            .uri("/api/v1/tarefas/$taskUuid")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.categoria.descricao")
            .isEqualTo("Casa")

        verify(exactly = 1) { buscarTarefa.execute(taskUuid, ownerUuid) }
    }

    @Test
    fun `lists tasks with cursor pagination`() {
        every { listarTarefas.execute(ownerUuid) } returns
            listOf(
                tarefa(titulo = "Segunda", dataHora = futureDate.plusSeconds(60)),
                tarefa(titulo = "Primeira", dataHora = futureDate),
            )

        mockMvc
            .perform(get("/api/v1/tarefas").param("limit", "1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items[0].titulo").value("Primeira"))
            .andExpect(jsonPath("$.nextCursor").isString())
    }

    @Test
    fun `lists tasks after decoded cursor without next page`() {
        val primeira = tarefa(titulo = "Primeira", id = taskUuid, dataHora = futureDate)
        val mesmoInstanteDepois = tarefa(titulo = "Mesmo instante", id = taskUuidAfter, dataHora = futureDate)
        val depois = tarefa(titulo = "Depois", id = taskUuidLater, dataHora = futureDate.plusSeconds(60))
        every { listarTarefas.execute(ownerUuid) } returns listOf(depois, primeira, mesmoInstanteDepois)

        mockMvc
            .perform(get("/api/v1/tarefas").param("cursor", CursorCodec.encode(primeira)).param("limit", "10"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items[0].titulo").value("Mesmo instante"))
            .andExpect(jsonPath("$.items[1].titulo").value("Depois"))
            .andExpect(jsonPath("$.nextCursor").doesNotExist())
    }

    @Test
    fun `returns empty page without next cursor`() {
        every { listarTarefas.execute(ownerUuid) } returns emptyList()

        mockMvc
            .perform(get("/api/v1/tarefas"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items").isEmpty)
            .andExpect(jsonPath("$.nextCursor").doesNotExist())
    }

    @Test
    fun `updates task`() {
        val command = slot<AtualizarTarefaCommand>()
        every { atualizarTarefa.execute(capture(command)) } returns tarefa(titulo = "Enviar relatorio")

        mockMvc
            .perform(
                put("/api/v1/tarefas/$taskUuid")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(validTaskPayload(titulo = "Enviar relatorio", categoriaId = trabalho.id.value)),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.titulo").value("Enviar relatorio"))

        command.captured.id shouldBe taskUuid
        command.captured.ownerId shouldBe ownerUuid
        command.captured.titulo shouldBe "Enviar relatorio"
        command.captured.categoriaId shouldBe trabalho.id.value
    }

    @Test
    fun `soft deletes task`() {
        every { excluirTarefa.execute(taskUuid, ownerUuid) } returns tarefa()

        mockMvc
            .perform(delete("/api/v1/tarefas/$taskUuid"))
            .andExpect(status().isNoContent)

        verify(exactly = 1) { excluirTarefa.execute(taskUuid, ownerUuid) }
    }

    @Test
    fun `changes task status`() {
        val command = slot<AlterarStatusTarefaCommand>()
        every { alterarStatusTarefa.execute(capture(command)) } returns tarefa(status = TarefaStatus.EM_ANDAMENTO)

        mockMvc
            .perform(post("/api/v1/tarefas/$taskUuid/status/em-andamento"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("EM_ANDAMENTO"))

        command.captured.id shouldBe taskUuid
        command.captured.ownerId shouldBe ownerUuid
        command.captured.status shouldBe TarefaStatus.EM_ANDAMENTO
    }

    @Test
    fun `changes task status to done`() {
        val command = slot<AlterarStatusTarefaCommand>()
        every { alterarStatusTarefa.execute(capture(command)) } returns tarefa(status = TarefaStatus.CONCLUIDA)

        mockMvc
            .perform(post("/api/v1/tarefas/$taskUuid/status/concluida"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("CONCLUIDA"))

        command.captured.status shouldBe TarefaStatus.CONCLUIDA
    }

    @Test
    fun `changes task status to canceled`() {
        val command = slot<AlterarStatusTarefaCommand>()
        every { alterarStatusTarefa.execute(capture(command)) } returns tarefa(status = TarefaStatus.CANCELADA)

        mockMvc
            .perform(post("/api/v1/tarefas/$taskUuid/status/cancelada"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("CANCELADA"))

        command.captured.status shouldBe TarefaStatus.CANCELADA
    }

    @Test
    fun `rejects invalid task status slug`() {
        mockMvc
            .perform(post("/api/v1/tarefas/$taskUuid/status/pendente"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.title").value("Requisicao invalida"))
    }

    @Test
    fun `lists task history`() {
        every { listarHistoricoTarefa.execute(taskUuid, ownerUuid) } returns
            listOf(
                TarefaHistorico(
                    id = UUID.fromString("018f95df-0c7b-7af2-a199-447f82f36931"),
                    tarefaId = TarefaId.from(taskUuid),
                    ownerId = UsuarioId.from(ownerUuid),
                    eventType = "TarefaCriada",
                    changedFields = emptySet(),
                    occurredAt = Instant.parse("2026-05-01T12:00:00Z"),
                ),
            )

        mockMvc
            .perform(get("/api/v1/tarefas/$taskUuid/historico"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].eventType").value("TarefaCriada"))

        verify(exactly = 1) { listarHistoricoTarefa.execute(taskUuid, ownerUuid) }
    }

    @Test
    fun `exports tasks as csv`() {
        every { listarTarefas.execute(ownerUuid) } returns listOf(tarefa(titulo = "Pagar, aluguel"))

        mockMvc
            .perform(get("/api/v1/tarefas/export.csv"))
            .andExpect(status().isOk)
            .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
            .andExpect { result ->
                result.response.contentAsString
                    .lines()
                    .first() shouldBe
                    "id,titulo,descricao,categoria,status,dataHora,criadaEm,atualizadaEm"
                result.response.contentAsString.contains("\"Pagar, aluguel\"") shouldBe true
            }
    }

    @Test
    fun `exports tasks as csv without quoting simple fields`() {
        every { listarTarefas.execute(ownerUuid) } returns listOf(tarefa(titulo = "Pagar aluguel"))

        mockMvc
            .perform(get("/api/v1/tarefas/export.csv"))
            .andExpect(status().isOk)
            .andExpect { result ->
                result.response.contentAsString.contains(",Pagar aluguel,") shouldBe true
                result.response.contentAsString.contains("\"Pagar aluguel\"") shouldBe false
            }
    }

    @Test
    fun `returns ProblemDetail for validation errors`() {
        mockMvc
            .perform(
                post("/api/v1/tarefas")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(validTaskPayload(titulo = "")),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.title").value("Requisicao invalida"))
            .andExpect(jsonPath("$.violations[0].field").value("titulo"))
    }

    @Test
    fun `returns ProblemDetail for not found task`() {
        every { buscarTarefa.execute(taskUuid, ownerUuid) } throws TarefaNaoEncontradaException(taskUuid)

        mockMvc
            .perform(get("/api/v1/tarefas/$taskUuid"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.title").value("Recurso nao encontrado"))
            .andExpect(jsonPath("$.detail").value("tarefa nao encontrada: $taskUuid"))
    }

    @Test
    fun `returns ProblemDetail for invalid cursor`() {
        every { listarTarefas.execute(ownerUuid) } returns emptyList()

        mockMvc
            .perform(get("/api/v1/tarefas").param("cursor", "not-a-cursor"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.title").value("Requisicao invalida"))
    }

    @Test
    fun `returns ProblemDetail for invalid query parameter`() {
        mockMvc
            .perform(get("/api/v1/tarefas").param("limit", "0"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.title").value("Parametro invalido"))
            .andExpect(jsonPath("$.violations[0].field").value("limit"))
    }

    @Test
    fun `returns ProblemDetail for malformed json`() {
        mockMvc
            .perform(
                post("/api/v1/tarefas")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"titulo":"""),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.title").value("JSON invalido"))
            .andExpect(jsonPath("$.detail").value("Corpo da requisicao ausente ou malformado"))
    }

    @Test
    fun `rejects unsupported content type`() {
        mockMvc
            .perform(
                post("/api/v1/tarefas")
                    .contentType(MediaType.APPLICATION_XML)
                    .content("<tarefa/>"),
            ).andExpect(status().isUnsupportedMediaType)
            .andExpect(jsonPath("$.title").value("Content-Type nao suportado"))
    }

    private fun validTaskPayload(
        titulo: String = "Pagar aluguel",
        categoriaId: UUID = casa.id.value,
    ): String =
        objectMapper.writeValueAsString(
            mapOf(
                "titulo" to titulo,
                "descricao" to "Vencimento do contrato residencial",
                "categoriaId" to categoriaId,
                "dataHora" to futureDate,
            ),
        )

    private companion object {
        val taskUuid: UUID = UUID.fromString("018f95df-0c7b-7af2-a199-447f82f36911")
        val ownerUuid: UUID = UUID.fromString("018f95df-0c7b-7af2-a199-447f82f36914")
        val taskUuidAfter: UUID = UUID.fromString("018f95df-0c7b-7af2-a199-447f82f36912")
        val taskUuidLater: UUID = UUID.fromString("018f95df-0c7b-7af2-a199-447f82f36913")
        val futureDate: Instant = Instant.parse("2030-05-01T13:00:00Z")
        val casa: Categoria =
            Categoria(
                id = CategoriaId.from(UUID.fromString("018f95df-0c7b-7af2-a199-447f82f36912")),
                descricao = DescricaoCategoria.of("Casa"),
            )
        val trabalho: Categoria =
            Categoria(
                id = CategoriaId.from(UUID.fromString("018f95df-0c7b-7af2-a199-447f82f36913")),
                descricao = DescricaoCategoria.of("Trabalho"),
            )

        fun tarefa(
            titulo: String = "Pagar aluguel",
            id: UUID = taskUuid,
            dataHora: Instant = futureDate,
            status: TarefaStatus = TarefaStatus.PENDENTE,
        ): Tarefa =
            Tarefa.reconstituir(
                id = TarefaId.from(id),
                ownerId = UsuarioId.from(ownerUuid),
                titulo = Titulo.of(titulo),
                descricao = Descricao.of("Vencimento do contrato residencial"),
                categoria = casa,
                dataHora = DataHoraTarefa.existente(dataHora),
                status = status,
                criadaEm = Instant.parse("2026-05-01T12:00:00Z"),
                atualizadaEm = Instant.parse("2026-05-01T12:00:00Z"),
                excluidaEm = null,
            )
    }
}
