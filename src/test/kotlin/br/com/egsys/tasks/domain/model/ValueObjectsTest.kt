package br.com.egsys.tasks.domain.model

import br.com.egsys.tasks.domain.exception.InvalidDomainValueException
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldHaveLength
import io.kotest.matchers.throwable.shouldHaveMessage
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class ValueObjectsTest {
    private val clock: Clock = Clock.fixed(Instant.parse("2026-05-01T12:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `ids reject nil uuid`() {
        val nilUuid = UUID(0L, 0L)

        assertThrows<InvalidDomainValueException> { TarefaId.from(nilUuid) }
            .shouldHaveMessage("tarefa.id nao pode ser um UUID nulo")
        assertThrows<InvalidDomainValueException> { CategoriaId.from(nilUuid) }
            .shouldHaveMessage("categoria.id nao pode ser um UUID nulo")
    }

    @Test
    fun `ids accept explicit uuid and can generate new values`() {
        val tarefaUuid = UUID.fromString("018f95df-0c7b-7af2-a199-447f82f36911")
        val categoriaUuid = UUID.fromString("018f95df-0c7b-7af2-a199-447f82f36912")

        TarefaId.from(tarefaUuid).value shouldBe tarefaUuid
        CategoriaId.from(categoriaUuid).value shouldBe categoriaUuid
        TarefaId.new().value.shouldBeInstanceOf<UUID>()
        CategoriaId.new().value.shouldBeInstanceOf<UUID>()
    }

    @Test
    fun `titulo trims value and enforces limits`() {
        Titulo.of("  Pagar aluguel  ").value shouldBe "Pagar aluguel"

        assertThrows<InvalidDomainValueException> { Titulo.of("   ") }
            .shouldHaveMessage("titulo nao pode ser vazio")
        assertThrows<InvalidDomainValueException> { Titulo.of("a".repeat(201)) }
            .shouldHaveMessage("titulo deve ter no maximo 200 caracteres")

        Titulo.of("a".repeat(200)).value.shouldHaveLength(200)
    }

    @Test
    fun `descricao is optional trimmed and size limited`() {
        Descricao.of(null).shouldBeNull()
        Descricao.of("   ").shouldBeNull()
        Descricao.of("  Comprar leite  ").shouldNotBeNull().value shouldBe "Comprar leite"
        Descricao.of("a".repeat(2_000))?.value.shouldHaveLength(2_000)

        assertThrows<InvalidDomainValueException> { Descricao.of("a".repeat(2_001)) }
            .shouldHaveMessage("descricao deve ter no maximo 2000 caracteres")
    }

    @Test
    fun `categoria descricao is required trimmed and size limited`() {
        DescricaoCategoria.of("  Casa  ").value shouldBe "Casa"
        DescricaoCategoria.of("a".repeat(120)).value.shouldHaveLength(120)

        assertThrows<InvalidDomainValueException> { DescricaoCategoria.of(" ") }
            .shouldHaveMessage("categoria.descricao nao pode ser vazia")
        assertThrows<InvalidDomainValueException> { DescricaoCategoria.of("a".repeat(121)) }
            .shouldHaveMessage("categoria.descricao deve ter no maximo 120 caracteres")
    }

    @Test
    fun `data hora rejects past dates for new tasks but restores existing schedules`() {
        val past = Instant.parse("2026-05-01T11:59:59Z")
        val now = Instant.parse("2026-05-01T12:00:00Z")
        val future = Instant.parse("2026-05-01T12:00:01Z")

        assertThrows<InvalidDomainValueException> { DataHoraTarefa.agendadaPara(past, clock) }
            .shouldHaveMessage("dataHora nao pode estar no passado")

        DataHoraTarefa.agendadaPara(now, clock).value shouldBe now
        DataHoraTarefa.agendadaPara(future, clock).value shouldBe future
        DataHoraTarefa.existente(past).value shouldBe past
    }
}
