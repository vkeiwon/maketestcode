package io.github.vkeiwon.junitgen.plugin

import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import io.github.vkeiwon.junitgen.core.AssertionStyle
import io.github.vkeiwon.junitgen.core.GeneratorOptions
import io.github.vkeiwon.junitgen.core.JUnitVersion

/** 생성된 테스트가 컴파일되는 데 필요한 라이브러리가 테스트 클래스패스에 있는지 확인한다. */
object TestLibraryChecker {

    private data class Library(val markerClass: String, val coordinates: String)

    fun missingLibraries(project: Project, testRoot: VirtualFile, options: GeneratorOptions): List<String> {
        val module = ModuleUtilCore.findModuleForFile(testRoot, project) ?: return emptyList()
        val scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, true)
        val facade = JavaPsiFacade.getInstance(project)
        return requiredLibraries(options)
            .filter { facade.findClass(it.markerClass, scope) == null }
            .map { it.coordinates }
    }

    private fun requiredLibraries(options: GeneratorOptions) = buildList {
        if (options.junitVersion == JUnitVersion.JUNIT5) {
            add(Library("org.junit.jupiter.api.Test", "org.junit.jupiter:junit-jupiter"))
            if (options.useMockito) add(Library("org.mockito.junit.jupiter.MockitoExtension", "org.mockito:mockito-junit-jupiter"))
        } else {
            add(Library("org.junit.Test", "junit:junit:4.13.2"))
            if (options.useMockito) add(Library("org.mockito.Mockito", "org.mockito:mockito-core"))
        }
        if (options.assertionStyle == AssertionStyle.ASSERTJ) add(Library("org.assertj.core.api.Assertions", "org.assertj:assertj-core"))
    }
}
