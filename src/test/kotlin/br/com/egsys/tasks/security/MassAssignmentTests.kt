package br.com.egsys.tasks.security

import br.com.egsys.tasks.application.usecase.AlterarStatusTarefaUseCase
import br.com.egsys.tasks.application.usecase.AtualizarTarefaUseCase
import br.com.egsys.tasks.application.usecase.BuscarTarefaUseCase
import br.com.egsys.tasks.application.usecase.CriarTarefaUseCase
import br.com.egsys.tasks.application.usecase.ExcluirTarefaUseCase
import br.com.egsys.tasks.application.usecase.ListarHistoricoTarefaUseCase
import br.com.egsys.tasks.application.usecase.ListarTarefasUseCase
import br.com.egsys.tasks.infrastructure.security.JwtService
import br.com.egsys.tasks.infrastructure.security.RateLimiterService
import br.com.egsys.tasks.web.controller.TarefaController
import br.com.egsys.tasks.web.exception.ApiExceptionHandler
import com.ninjasquad.springmockk.MockkBean
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(controllers = [TarefaController::class])
@AutoConfigureMockMvc(addFilters = false)
@Import(ApiExceptionHandler::class)
@WithMockUser(username = "018f95df-0c7b-7af2-a199-447f82f36914", roles = ["USER"])
@Suppress("UnusedPrivateProperty")
class MassAssignmentTests {
    @Autowired
    private lateinit var mockMvc: MockMvc

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

    @Test
    fun `rejeita campos de identidade e autorizacao enviados pelo cliente`() {
        mockMvc
            .perform(
                post("/api/v1/tarefas")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "titulo": "Tarefa hostil",
                          "descricao": "Cliente tentou escolher owner e role",
                          "categoriaId": "018f95df-0c7b-7af2-a199-447f82f36912",
                          "dataHora": "2030-05-01T13:00:00Z",
                          "ownerId": "018f95df-0c7b-7af2-a199-447f82f36999",
                          "userId": "018f95df-0c7b-7af2-a199-447f82f36999",
                          "role": "ROLE_ADMIN",
                          "isAdmin": true
                        }
                        """.trimIndent(),
                    ),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.title").value("JSON invalido"))

        verify(exactly = 0) { criarTarefa.execute(any()) }
    }
}
