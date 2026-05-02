package br.com.egsys.tasks.playground

import br.com.egsys.tasks.web.controller.PlaygroundController
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Profile

class PlaygroundProfileTests {
    @Test
    fun `controller is gated by Profile dev`() {
        val annotation = PlaygroundController::class.java.getAnnotation(Profile::class.java)

        annotation.shouldNotBeNull()
        annotation.value.contains("dev").shouldBeTrue()
    }

    @Test
    fun `playground html resource exists in classpath`() {
        val resource = javaClass.classLoader.getResource("playground/playground.html")

        resource.shouldNotBeNull()
    }

    @Test
    fun `playground html does not live under static so it never leaks via static resource handler`() {
        val staticLeak = javaClass.classLoader.getResource("static/playground.html")

        staticLeak.shouldBeNull()
    }

    @Test
    fun `bean is registered when dev profile is active`() {
        val context =
            AnnotationConfigApplicationContext().apply {
                environment.setActiveProfiles("dev")
                register(PlaygroundController::class.java)
                refresh()
            }

        try {
            context.getBeanNamesForType(PlaygroundController::class.java).size.shouldBe(1)
        } finally {
            context.close()
        }
    }

    @Test
    fun `bean is absent without dev profile`() {
        val context =
            AnnotationConfigApplicationContext().apply {
                environment.setActiveProfiles("prod")
                register(PlaygroundController::class.java)
                refresh()
            }

        try {
            context.getBeanNamesForType(PlaygroundController::class.java).size.shouldBe(0)
        } finally {
            context.close()
        }
    }
}
