package br.com.egsys.tasks.support

import br.com.egsys.tasks.infrastructure.persistence.adapter.JpaCategoriaRepository
import br.com.egsys.tasks.infrastructure.persistence.adapter.JpaTarefaRepository
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName

@DataJpaTest
@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Suppress("UtilityClassWithPublicConstructor")
@Import(
    JpaCategoriaRepository::class,
    JpaTarefaRepository::class,
)
abstract class PersistenceIntegrationTest {
    companion object {
        @Container
        @JvmField
        val postgres: PostgreSQLContainer<*> =
            PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
                .apply {
                    withDatabaseName("egsys_tasks_it")
                    withUsername("egsys_it")
                    withPassword("egsys_it")
                }

        @DynamicPropertySource
        @JvmStatic
        fun databaseProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("spring.jpa.hibernate.ddl-auto") { "validate" }
            registry.add("spring.jpa.properties.hibernate.generate_statistics") { "true" }
            registry.add("spring.flyway.enabled") { "true" }
        }
    }
}
