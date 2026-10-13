package io.github.vkeiwon.junitgen.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Path

/**
 * 모든 옵션 조합에 대해 "생성 → javac 컴파일 → JUnit 실행"을 해 보고,
 * 생성된 테스트가 컴파일되며 @Disabled/@Ignore 가 아닌 테스트는 모두 통과하는지 확인한다.
 */
class GeneratedTestEndToEndTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("optionCombinations")
    fun `generated tests compile and pass`(options: GeneratorOptions, @TempDir workDir: Path) {
        val generator = JUnitTestGenerator(options)
        val tests = SampleModels.all.map(generator::generate)
        val compiler = GeneratedTestCompiler(workDir)

        // 모든 샘플은 항상 컴파일되어야 한다.
        val classes = compiler.compile(tests)

        // Mockito 없이는 협력 객체가 null 로 채워지므로 협력 객체가 없는 Calculator 만 실행해 본다.
        val runnable = if (options.useMockito) tests else tests.filter { it.className == "CalculatorTest" }
        val summary = compiler.run(classes, runnable)

        val failures = StringWriter().also { summary.printFailuresTo(PrintWriter(it), 20) }
        assertEquals(0, summary.totalFailureCount, "실패한 생성 테스트:\n$failures")
        val exceptionTests = if (options.generateExceptionTests) (if (options.useMockito) 2L else 1L) else 0L
        val successTests = if (options.useMockito) 14L else 6L
        assertEquals(successTests, summary.testsSucceededCount)
        assertEquals(exceptionTests, summary.testsSkippedCount)
    }

    companion object {
        @JvmStatic
        fun optionCombinations(): List<GeneratorOptions> = buildList {
            for (version in JUnitVersion.entries) for (style in AssertionStyle.entries) for (mockito in listOf(true, false)) {
                add(GeneratorOptions(junitVersion = version, assertionStyle = style, useMockito = mockito))
            }
            add(GeneratorOptions(generateExceptionTests = false, generateDisplayNames = false, skipAccessors = false))
        }
    }
}
