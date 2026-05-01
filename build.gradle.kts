import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.concurrent.TimeUnit

plugins {
    kotlin("jvm") version "2.3.10"
    kotlin("plugin.spring") version "2.3.10"
    kotlin("plugin.jpa") version "2.3.10"
    id("org.springframework.boot") version "3.5.14"
    id("org.jlleitschuh.gradle.ktlint") version "14.1.0"
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
    id("info.solidsoft.pitest") version "1.19.0"
    jacoco
}

group = "br.com.egsys"
version = "0.1.0-SNAPSHOT"
description = "API RESTful de tarefas em Kotlin e Spring Boot para o teste tecnico EGSYS."

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

val jjwtVersion = "0.13.0"
val springdocVersion = "2.8.17"
val bouncyCastleVersion = "1.84"
val logstashLogbackVersion = "9.0"
val kotestVersion = "6.1.11"
val mockkVersion = "1.14.9"
val springMockkVersion = "4.0.2"
val archUnitVersion = "1.4.1"
val jacocoCoverageExclusions =
    listOf(
        "**/TasksApplication.class",
        "**/TasksApplicationKt.class",
    )
val dockerProbeTimeoutSeconds = 3L

fun isDockerAvailable(): Boolean =
    runCatching {
        val process =
            ProcessBuilder("docker", "info", "--format", "{{.ServerVersion}}")
                .redirectErrorStream(true)
                .start()

        if (!process.waitFor(dockerProbeTimeoutSeconds, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            return false
        }

        process.exitValue() == 0
    }.getOrDefault(false)

fun coverageClassDirectories() =
    files(
        sourceSets.main.get().output.classesDirs.map {
            fileTree(it) {
                exclude(jacocoCoverageExclusions)
            }
        },
    )

dependencies {
    implementation(platform(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES))

    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("io.micrometer:micrometer-tracing-bridge-otel")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp")
    implementation("io.jsonwebtoken:jjwt-api:$jjwtVersion")
    implementation("org.bouncycastle:bcprov-jdk18on:$bouncyCastleVersion")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:$springdocVersion")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstashLogbackVersion")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:$jjwtVersion")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:$jjwtVersion")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "org.mockito")
    }
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("io.kotest:kotest-assertions-core-jvm:$kotestVersion")
    testImplementation("io.mockk:mockk:$mockkVersion")
    testImplementation("com.ninja-squad:springmockk:$springMockkVersion")
    testImplementation("com.tngtech.archunit:archunit-junit5:$archUnitVersion")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:testcontainers")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

tasks.withType<Test>().configureEach {
    val dockerAvailable = isDockerAvailable()

    useJUnitPlatform {
        if (!dockerAvailable) {
            excludeTags("postgres")
        }
    }
    systemProperty("spring.profiles.active", "test")
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    classDirectories.setFrom(coverageClassDirectories())
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    classDirectories.setFrom(coverageClassDirectories())
    onlyIf {
        coverageClassDirectories().files.isNotEmpty()
    }
    violationRules {
        rule {
            limit {
                minimum = "0.85".toBigDecimal()
            }
        }
        rule {
            element = "BUNDLE"
            limit {
                counter = "BRANCH"
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.ktlintCheck)
    dependsOn(tasks.detekt)
    dependsOn(tasks.jacocoTestCoverageVerification)
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(files("config/detekt/detekt.yml"))
}

pitest {
    targetClasses.set(setOf("br.com.egsys.tasks.domain.*", "br.com.egsys.tasks.infrastructure.security.*"))
    targetTests.set(setOf("br.com.egsys.tasks.*"))
    junit5PluginVersion.set("1.2.2")
    threads.set(4)
    outputFormats.set(setOf("XML", "HTML"))
    timestampedReports.set(false)
    mutationThreshold.set(70)
}
