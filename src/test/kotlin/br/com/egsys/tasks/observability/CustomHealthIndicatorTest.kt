package br.com.egsys.tasks.observability

import br.com.egsys.tasks.infrastructure.observability.DatabaseReadinessHealthIndicator
import br.com.egsys.tasks.infrastructure.observability.RedisReadinessHealthIndicator
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.boot.actuate.health.Status
import org.springframework.dao.QueryTimeoutException
import org.springframework.data.redis.RedisConnectionFailureException
import org.springframework.data.redis.connection.RedisConnection
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.jdbc.core.JdbcTemplate

class CustomHealthIndicatorTest {
    @Test
    fun `database health fica UP quando SELECT 1 responde`() {
        val jdbcTemplate =
            mockk<JdbcTemplate> {
                every { queryForObject("SELECT 1", Int::class.java) } returns 1
            }

        val health = DatabaseReadinessHealthIndicator(jdbcTemplate).health()

        health.status shouldBe Status.UP
        health.details["database"] shouldBe "postgresql"
    }

    @Test
    fun `database health fica DOWN quando consulta falha`() {
        val jdbcTemplate =
            mockk<JdbcTemplate> {
                every { queryForObject("SELECT 1", Int::class.java) } throws QueryTimeoutException("timeout")
            }

        val health = DatabaseReadinessHealthIndicator(jdbcTemplate).health()

        health.status shouldBe Status.DOWN
    }

    @Test
    fun `database health fica DOWN quando SELECT 1 retorna valor inesperado`() {
        val jdbcTemplate =
            mockk<JdbcTemplate> {
                every { queryForObject("SELECT 1", Int::class.java) } returns 0
            }

        val health = DatabaseReadinessHealthIndicator(jdbcTemplate).health()

        health.status shouldBe Status.DOWN
        health.details["reason"] shouldBe "unexpected validation result"
    }

    @Test
    fun `redis health fica UP quando ping responde PONG`() {
        val connection =
            mockk<RedisConnection> {
                every { ping() } returns "PONG"
            }
        justRun { connection.close() }
        val factory =
            mockk<RedisConnectionFactory> {
                every { getConnection() } returns connection
            }

        val health = RedisReadinessHealthIndicator(factory).health()

        health.status shouldBe Status.UP
        health.details["redis"] shouldBe "reachable"
    }

    @Test
    fun `redis health fica DOWN quando conexao falha`() {
        val factory =
            mockk<RedisConnectionFactory> {
                every { getConnection() } throws RedisConnectionFailureException("unreachable")
            }

        val health = RedisReadinessHealthIndicator(factory).health()

        health.status shouldBe Status.DOWN
    }

    @Test
    fun `redis health fica DOWN quando ping retorna valor inesperado`() {
        val connection =
            mockk<RedisConnection> {
                every { ping() } returns "NOPE"
            }
        justRun { connection.close() }
        val factory =
            mockk<RedisConnectionFactory> {
                every { getConnection() } returns connection
            }

        val health = RedisReadinessHealthIndicator(factory).health()

        health.status shouldBe Status.DOWN
        health.details["redis"] shouldBe "unexpected ping response"
    }
}
