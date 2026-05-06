package br.com.egsys.tasks.playground

import br.com.egsys.tasks.web.controller.PlaygroundController
import br.com.egsys.tasks.web.controller.PlaygroundProperties
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Profile
import org.springframework.http.MediaType
import org.springframework.stereotype.Controller
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class PlaygroundAvailabilityTests {
    @Test
    fun `controller is gated by property instead of profile`() {
        PlaygroundController::class.java.getAnnotation(Profile::class.java).shouldBeNull()

        val annotation = PlaygroundController::class.java.getAnnotation(ConditionalOnProperty::class.java)

        annotation.shouldNotBeNull()
        annotation.prefix.shouldBe("egsys.playground")
        annotation.name.toList().shouldContain("enabled")
        annotation.havingValue.shouldBe("true")
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
    fun `bean is registered when playground is enabled`() {
        contextRunner
            .withPropertyValues("egsys.playground.enabled=true")
            .run { context ->
                context.getBeanNamesForType(PlaygroundController::class.java).size.shouldBe(1)
            }
    }

    @Test
    fun `bean is absent when playground is disabled`() {
        contextRunner
            .withPropertyValues("egsys.playground.enabled=false")
            .run { context ->
                context.getBeanNamesForType(PlaygroundController::class.java).size.shouldBe(0)
            }
    }

    @Test
    fun `playground responds 200 when enabled`() {
        val controller = PlaygroundController(PlaygroundProperties(enabled = true, attackDemosEnabled = true))
        val mvc =
            MockMvcBuilders
                .standaloneSetup(controller)
                .build()

        mvc
            .perform(get("/playground"))
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
    }

    @Test
    fun `playground responds 404 when no handler is registered`() {
        val mvc =
            MockMvcBuilders
                .standaloneSetup(EmptyController())
                .build()

        mvc
            .perform(get("/playground"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `config returns attack demos disabled in production controlled mode`() {
        val controller = PlaygroundController(PlaygroundProperties(enabled = true, attackDemosEnabled = false))
        val mvc =
            MockMvcBuilders
                .standaloneSetup(controller)
                .build()

        mvc
            .perform(get("/playground/config"))
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.attackDemosEnabled").value(false))
            .andExpect(jsonPath("$.metricsEnabled").value(false))
    }

    private val contextRunner =
        ApplicationContextRunner()
            .withUserConfiguration(PlaygroundControllerTestConfig::class.java)

    @Configuration
    @EnableConfigurationProperties(PlaygroundProperties::class)
    @Import(PlaygroundController::class)
    private class PlaygroundControllerTestConfig

    @Controller
    private class EmptyController
}
