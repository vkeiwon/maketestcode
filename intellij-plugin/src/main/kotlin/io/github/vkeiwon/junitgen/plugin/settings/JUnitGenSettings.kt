package io.github.vkeiwon.junitgen.plugin.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import io.github.vkeiwon.junitgen.core.AssertionStyle
import io.github.vkeiwon.junitgen.core.GeneratorOptions
import io.github.vkeiwon.junitgen.core.JUnitVersion

@Service(Service.Level.APP)
@State(name = "JUnitTestGeneratorSettings", storages = [Storage("junitTestGenerator.xml")])
class JUnitGenSettings : PersistentStateComponent<JUnitGenSettings.Options> {

    class Options {
        var junitVersion: JUnitVersion = JUnitVersion.JUNIT5
        var assertionStyle: AssertionStyle = AssertionStyle.JUNIT
        var useMockito: Boolean = true
        var generateDisplayNames: Boolean = true
        var generateExceptionTests: Boolean = true
        var skipAccessors: Boolean = true
        var testClassSuffix: String = "Test"
    }

    private var options = Options()

    override fun getState(): Options = options

    override fun loadState(state: Options) {
        options = state
    }

    fun toOptions() = GeneratorOptions(
        junitVersion = options.junitVersion,
        useMockito = options.useMockito,
        assertionStyle = options.assertionStyle,
        generateDisplayNames = options.generateDisplayNames,
        generateExceptionTests = options.generateExceptionTests,
        skipAccessors = options.skipAccessors,
        testClassSuffix = options.testClassSuffix.ifBlank { "Test" },
    )

    companion object {
        fun getInstance(): JUnitGenSettings = service()
    }
}
