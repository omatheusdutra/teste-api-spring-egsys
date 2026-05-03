package br.com.egsys.tasks.web.controller

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "egsys.playground")
data class PlaygroundProperties(
    var enabled: Boolean = false,
    var attackDemosEnabled: Boolean = false,
)
