package br.com.egsys.tasks.infrastructure.persistence

import br.com.egsys.tasks.support.PersistenceIntegrationTest
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Tag("postgres")
class FlywayPersistenceIntegrationTest : PersistenceIntegrationTest() {
    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `runs against PostgreSQL and applies category seed`() {
        val databaseName = jdbcTemplate.queryForObject("select current_database()", String::class.java)
        val version = jdbcTemplate.queryForObject("select version()", String::class.java)
        val categorias =
            jdbcTemplate.queryForList(
                "select descricao from categorias order by descricao",
                String::class.java,
            )

        databaseName shouldBe "egsys_tasks_it"
        version.shouldContain("PostgreSQL")
        categorias shouldContainAll listOf("Casa", "Trabalho")
    }

    @Test
    fun `schema has search indexes and restrictive category foreign key`() {
        val indexes =
            jdbcTemplate.queryForList(
                "select indexname from pg_indexes where schemaname = 'public'",
                String::class.java,
            )
        val categoriaId = UUID.fromString("018f95df-0c7b-7af2-a199-447f82f36931")
        val tarefaId = UUID.fromString("018f95df-0c7b-7af2-a199-447f82f36932")
        val scheduledAt = Timestamp.from(Instant.parse("2026-05-01T13:00:00Z"))
        val createdAt = Timestamp.from(Instant.parse("2026-05-01T12:00:00Z"))

        indexes shouldContainAll
            listOf(
                "idx_categorias_descricao",
                "idx_tarefas_categoria_id",
                "idx_tarefas_data_hora_id_active",
                "idx_tarefas_status_active",
                "idx_tarefas_owner_data_hora_id_active",
            )

        jdbcTemplate.update(
            "insert into categorias (id, descricao) values (?, ?)",
            categoriaId,
            "Restrita",
        )
        jdbcTemplate.update(
            """
            insert into tarefas (id, owner_id, titulo, descricao, categoria_id, data_hora, status, criada_em, atualizada_em)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            tarefaId,
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            "Validar FK",
            null,
            categoriaId,
            scheduledAt,
            "PENDENTE",
            createdAt,
            createdAt,
        )

        assertThrows<DataIntegrityViolationException> {
            jdbcTemplate.update("delete from categorias where id = ?", categoriaId)
        }
    }
}
