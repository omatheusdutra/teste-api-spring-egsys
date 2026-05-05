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
    fun `render blueprint declares controlled production demo`() {
        val blueprint = Path("render.yaml").readText()

        blueprint shouldContain "runtime: docker"
        blueprint shouldContain "type: keyvalue"
        blueprint shouldContain "egsys-tasks-postgres"
        blueprint shouldContain "SPRING_PROFILES_ACTIVE"
        blueprint shouldContain "EGSYS_REDIS_URL"
        blueprint shouldContain "EGSYS_JWT_PRIVATE_KEY"
        blueprint shouldContain "sync: false"
        blueprint shouldContain "healthCheckPath: /actuator/health"
    }

    @Test
    fun `prod profile accepts render managed services`() {
        val application = Path("src/main/resources/application.yml").readText()
        val prod = Path("src/main/resources/application-prod.yml").readText()

        application shouldContain "port: \${PORT:8080}"
        prod shouldContain "jdbc:postgresql://\${EGSYS_DB_HOST}:\${EGSYS_DB_PORT:5432}/\${EGSYS_DB_NAME}"
        prod shouldContain "url: \${EGSYS_REDIS_URL}"
    }

    @Test
    fun `devex helpers and api collection are committed`() {
        Path("Makefile").readText() shouldContain "check:"
        Path("bruno/egsys-tasks-api/bruno.json").readText() shouldContain "EGSYS Tasks API"
        Path("config/prometheus/prometheus.yml").readText() shouldContain "teste-api-spring-egsys"
    }
}
