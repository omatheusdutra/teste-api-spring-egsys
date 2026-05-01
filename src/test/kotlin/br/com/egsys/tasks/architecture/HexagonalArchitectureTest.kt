package br.com.egsys.tasks.architecture

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.junit.jupiter.api.Test

class HexagonalArchitectureTest {
    private val importedClasses: JavaClasses =
        ClassFileImporter().importPackages("br.com.egsys.tasks")

    @Test
    fun `domain has no Spring Jakarta Jackson or persistence dependencies`() {
        noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "org.springframework..",
                "jakarta..",
                "com.fasterxml.jackson..",
                "org.hibernate..",
                "javax.persistence..",
            ).allowEmptyShould(true)
            .check(importedClasses)
    }

    @Test
    fun `application depends only on domain and JDK`() {
        noClasses()
            .that()
            .resideInAPackage("..application..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "br.com.egsys.tasks.infrastructure..",
                "br.com.egsys.tasks.web..",
                "org.springframework..",
                "jakarta..",
                "com.fasterxml.jackson..",
                "org.hibernate..",
            ).allowEmptyShould(true)
            .check(importedClasses)
    }

    @Test
    fun `web never depends on JPA entities`() {
        noClasses()
            .that()
            .resideInAPackage("..web..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..infrastructure.persistence.entity..")
            .allowEmptyShould(true)
            .check(importedClasses)
    }
}
