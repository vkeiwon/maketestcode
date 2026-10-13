package io.github.vkeiwon.junitgen.core

enum class JUnitVersion(private val displayName: String) {
    JUNIT5("JUnit 5 (Jupiter)"),
    JUNIT4("JUnit 4");

    override fun toString() = displayName
}

enum class AssertionStyle(private val displayName: String) {
    JUNIT("JUnit Assertions"),
    ASSERTJ("AssertJ");

    override fun toString() = displayName
}

data class GeneratorOptions(
    val junitVersion: JUnitVersion = JUnitVersion.JUNIT5,
    val useMockito: Boolean = true,
    val assertionStyle: AssertionStyle = AssertionStyle.JUNIT,
    val generateDisplayNames: Boolean = true,
    val generateExceptionTests: Boolean = true,
    val skipAccessors: Boolean = true,
    val testClassSuffix: String = "Test",
)
