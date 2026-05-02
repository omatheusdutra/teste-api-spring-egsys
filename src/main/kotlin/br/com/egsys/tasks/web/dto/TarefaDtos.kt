package br.com.egsys.tasks.web.dto

import br.com.egsys.tasks.domain.model.TarefaStatus
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.FutureOrPresent
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

@JsonIgnoreProperties(ignoreUnknown = false)
@Schema(name = "CriarTarefaRequest")
data class CriarTarefaRequest(
    @field:NotBlank(message = "{tarefa.titulo.obrigatorio}")
    @field:Size(max = 200, message = "{tarefa.titulo.tamanho}")
    @field:Schema(example = "Pagar aluguel")
    val titulo: String?,
    @field:Size(max = 2000, message = "{tarefa.descricao.tamanho}")
    @field:Schema(example = "Vencimento do contrato residencial")
    val descricao: String?,
    @field:NotNull(message = "{tarefa.categoriaId.obrigatorio}")
    @field:Schema(example = "018f95df-0c7b-7af2-a199-447f82f36912")
    val categoriaId: UUID?,
    @field:NotNull(message = "{tarefa.dataHora.obrigatorio}")
    @field:FutureOrPresent(message = "{tarefa.dataHora.futuroOuPresente}")
    @field:Schema(example = "2026-05-01T13:00:00Z")
    val dataHora: Instant?,
)

@JsonIgnoreProperties(ignoreUnknown = false)
@Schema(name = "AtualizarTarefaRequest")
data class AtualizarTarefaRequest(
    @field:NotBlank(message = "{tarefa.titulo.obrigatorio}")
    @field:Size(max = 200, message = "{tarefa.titulo.tamanho}")
    @field:Schema(example = "Enviar relatorio")
    val titulo: String?,
    @field:Size(max = 2000, message = "{tarefa.descricao.tamanho}")
    @field:Schema(example = "Enviar PDF para a diretoria")
    val descricao: String?,
    @field:NotNull(message = "{tarefa.categoriaId.obrigatorio}")
    @field:Schema(example = "018f95df-0c7b-7af2-a199-447f82f36913")
    val categoriaId: UUID?,
    @field:NotNull(message = "{tarefa.dataHora.obrigatorio}")
    @field:FutureOrPresent(message = "{tarefa.dataHora.futuroOuPresente}")
    @field:Schema(example = "2026-05-01T14:00:00Z")
    val dataHora: Instant?,
)

@Schema(name = "TarefaResponse")
data class TarefaResponse(
    val id: UUID,
    val titulo: String,
    val descricao: String?,
    val categoria: CategoriaResponse,
    val dataHora: Instant,
    val status: TarefaStatus,
    val criadaEm: Instant,
    val atualizadaEm: Instant,
)

@Schema(name = "TarefaPageResponse")
data class TarefaPageResponse(
    val items: List<TarefaResponse>,
    val nextCursor: String?,
)

@Schema(name = "TarefaHistoricoResponse")
data class TarefaHistoricoResponse(
    val id: UUID,
    val tarefaId: UUID,
    val eventType: String,
    val changedFields: Set<String>,
    val occurredAt: Instant,
)
