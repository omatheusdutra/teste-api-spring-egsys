package br.com.egsys.tasks

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class TasksApplicationSmokeTest {
    @Test
    fun `application package is stable`() {
        TasksApplication::class.java.packageName shouldBe "br.com.egsys.tasks"
    }
}
