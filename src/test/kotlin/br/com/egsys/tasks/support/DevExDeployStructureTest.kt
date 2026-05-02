package br.com.egsys.tasks.support

import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import kotlin.io.path.Path
import kotlin.io.path.readText

class DevExDeployStructureTest {
    @Test
    fun `dockerfile is multi stage and uses minimal runtime image`() {
        val dockerfile = Path("Dockerfile").readText()

        dockerfile shouldContain "FROM eclipse-temurin:21-jdk-alpine AS build"
        dockerfile shouldContain "FROM gcr.io/distroless/java21-debian12:nonroot"
        dockerfile shouldContain "USER nonroot:nonroot"
    }

    @Test
    fun `docker compose declares production-like local stack`() {
        val compose = Path("docker-compose.yml").readText()

        compose shouldContain "postgres:"
        compose shouldContain "redis:"
        compose shouldContain "prometheus:"
        compose shouldContain "grafana:"
        compose shouldContain "teste-api-spring-egsys:"
    }

    @Test
    fun `devex helpers and api collection are committed`() {
        Path("Makefile").readText() shouldContain "check:"
        Path("bruno/egsys-tasks-api/bruno.json").readText() shouldContain "EGSYS Tasks API"
        Path("config/prometheus/prometheus.yml").readText() shouldContain "teste-api-spring-egsys"
    }
}
