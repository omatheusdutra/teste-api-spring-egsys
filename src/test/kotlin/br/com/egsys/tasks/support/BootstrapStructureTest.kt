package br.com.egsys.tasks.support

import io.kotest.matchers.collections.shouldContainAll
import org.junit.jupiter.api.Test
import kotlin.io.path.Path
import kotlin.io.path.exists

class BootstrapStructureTest {
    @Test
    fun `hexagonal source packages exist`() {
        val sourceRoot = Path("src/main/kotlin/br/com/egsys/tasks")
        val expectedPackages =
            listOf(
                "domain/model",
                "domain/port",
                "domain/exception",
                "application/usecase",
                "application/service",
                "infrastructure/persistence",
                "infrastructure/security",
                "infrastructure/messaging",
                "infrastructure/config",
                "web/controller",
                "web/dto",
                "web/mapper",
                "web/exception",
            )

        val existingPackages = expectedPackages.filter { sourceRoot.resolve(it).exists() }

        existingPackages shouldContainAll expectedPackages
    }
}
