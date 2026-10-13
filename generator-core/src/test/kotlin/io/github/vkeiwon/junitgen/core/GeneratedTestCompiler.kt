package io.github.vkeiwon.junitgen.core

import org.junit.platform.launcher.core.LauncherFactory
import org.junit.platform.launcher.listeners.SummaryGeneratingListener
import org.junit.platform.launcher.listeners.TestExecutionSummary
import org.junit.platform.engine.discovery.DiscoverySelectors
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder
import java.io.File
import java.io.StringWriter
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import javax.tools.ToolProvider
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

/** 샘플 소스 + 생성된 테스트를 실제로 javac 로 컴파일하고 JUnit Platform 으로 실행한다. */
class GeneratedTestCompiler(private val workDir: Path) {

    private val samplesDir: Path = Path.of(javaClass.getResource("/samples")!!.toURI())

    fun compile(tests: List<GeneratedTest>): Path {
        val sources = mutableListOf<File>()
        Files.walk(samplesDir).use { stream ->
            stream.filter { it.toString().endsWith(".java") }.forEach { sources += it.toFile() }
        }
        val testSrc = workDir.resolve("test-src")
        tests.forEach { test ->
            val file = testSrc.resolve(test.packageName.replace('.', '/')).resolve(test.fileName)
            file.parent.createDirectories()
            file.writeText(test.source)
            sources += file.toFile()
        }
        val out = workDir.resolve("classes").createDirectories()
        val compiler = ToolProvider.getSystemJavaCompiler()
        val diagnostics = StringWriter()
        val fileManager = compiler.getStandardFileManager(null, null, null)
        val ok = compiler.getTask(
            diagnostics, fileManager, null,
            listOf("-d", out.toString(), "-cp", System.getProperty("java.class.path"), "-Xlint:none", "-proc:none"),
            null, fileManager.getJavaFileObjectsFromFiles(sources),
        ).call()
        check(ok) {
            "생성된 테스트가 컴파일되지 않습니다:\n$diagnostics\n" + tests.joinToString("\n\n") { "// ${it.fileName}\n${it.source}" }
        }
        return out
    }

    fun run(classesDir: Path, tests: List<GeneratedTest>): TestExecutionSummary {
        val loader = URLClassLoader(arrayOf(classesDir.toUri().toURL()), javaClass.classLoader)
        val previous = Thread.currentThread().contextClassLoader
        Thread.currentThread().contextClassLoader = loader
        try {
            val request = LauncherDiscoveryRequestBuilder.request()
                .selectors(tests.map { DiscoverySelectors.selectClass(loader.loadClass("${it.packageName}.${it.className}")) })
                .build()
            val listener = SummaryGeneratingListener()
            LauncherFactory.create().execute(request, listener)
            return listener.summary
        } finally {
            Thread.currentThread().contextClassLoader = previous
        }
    }
}
