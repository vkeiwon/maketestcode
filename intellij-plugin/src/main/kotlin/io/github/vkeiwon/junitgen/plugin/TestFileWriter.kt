package io.github.vkeiwon.junitgen.plugin

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.psi.codeStyle.CodeStyleManager
import io.github.vkeiwon.junitgen.core.GeneratedTest
import org.jetbrains.jps.model.java.JavaSourceRootType

/** 생성된 테스트 소스를 테스트 소스 루트에 쓰거나, 이미 있으면 빠진 테스트만 병합한다. */
class TestFileWriter(private val project: Project) {

    data class Result(val file: PsiJavaFile, val created: Boolean, val addedTests: Int)

    /**
     * 소스 파일에 대응하는 테스트 소스 루트를 찾는다. 없으면 `src/main/java` 옆에 `src/test/java` 를 만든다.
     * write action 안에서 호출해야 한다.
     */
    fun findOrCreateTestRoot(sourceFile: VirtualFile): VirtualFile? {
        val index = ProjectFileIndex.getInstance(project)
        val sourceRoot = index.getSourceRootForFile(sourceFile) ?: return null
        val module = index.getModuleForFile(sourceFile)

        val ownRoots = module?.let { ModuleRootManager.getInstance(it).getSourceRoots(JavaSourceRootType.TEST_SOURCE) }.orEmpty()
        pickClosest(ownRoots, sourceRoot)?.let { return it }

        // Gradle 의 "source set 별 모듈" 구조에서는 main 과 test 가 서로 다른 모듈이다. 같은 src 폴더 아래의 test 루트를 찾는다.
        val siblingRoots = ModuleManager.getInstance(project).modules
            .flatMap { ModuleRootManager.getInstance(it).getSourceRoots(JavaSourceRootType.TEST_SOURCE) }
            .filter { commonSegments(it, sourceRoot) >= segments(sourceRoot).size - 2 }
        pickClosest(siblingRoots, sourceRoot)?.let { return it }

        // 관례적인 Maven/Gradle 레이아웃이면 src/test/<언어> 를 만든다.
        val language = sourceRoot.name
        val main = sourceRoot.parent ?: return null
        val src = main.parent ?: return null
        if (main.name != "main") return null
        return VfsUtil.createDirectoryIfMissing(src, "test/$language")
    }

    /** write action 안에서 호출해야 한다. */
    fun write(generated: GeneratedTest, testRoot: VirtualFile): Result {
        val directoryFile = if (generated.packageName.isEmpty()) testRoot
        else VfsUtil.createDirectoryIfMissing(testRoot, generated.packageName.replace('.', '/'))
        val directory = PsiManager.getInstance(project).findDirectory(directoryFile)
            ?: error("디렉터리를 찾을 수 없습니다: ${directoryFile.path}")
        val generatedFile = PsiFileFactory.getInstance(project)
            .createFileFromText(generated.fileName, JavaFileType.INSTANCE, generated.source) as PsiJavaFile

        val existing = directory.findFile(generated.fileName) as? PsiJavaFile
        if (existing == null) {
            val added = directory.add(generatedFile) as PsiJavaFile
            CodeStyleManager.getInstance(project).reformat(added)
            return Result(added, created = true, addedTests = generated.testMethodNames.size)
        }
        return Result(existing, created = false, addedTests = merge(existing, generatedFile, generated.testMethodNames.toSet()))
    }

    /** 기존 테스트 클래스에 없는 import, 클래스 애노테이션, 필드, 메서드만 추가한다. 추가된 테스트 수를 돌려준다. */
    private fun merge(existing: PsiJavaFile, generated: PsiJavaFile, testMethodNames: Set<String>): Int {
        val target = existing.classes.firstOrNull() ?: return 0
        val source = generated.classes.firstOrNull() ?: return 0
        val addedElements = mutableListOf<PsiElement>()

        existing.importList?.let { importList ->
            val present = importList.allImportStatements.map { it.text }.toSet()
            generated.importList?.allImportStatements
                ?.filter { it.text !in present }
                ?.forEach { importList.add(it.copy()) }
        }

        val targetModifiers = target.modifierList
        if (targetModifiers != null) {
            source.modifierList?.annotations?.forEach { annotation ->
                val name = annotation.nameReferenceElement?.referenceName
                if (targetModifiers.annotations.none { it.nameReferenceElement?.referenceName == name }) {
                    val anchor = targetModifiers.firstChild
                    addedElements += if (anchor != null) targetModifiers.addBefore(annotation.copy(), anchor) else targetModifiers.add(annotation.copy())
                }
            }
        }

        val firstMethod = target.methods.firstOrNull()
        source.fields.filter { target.findFieldByName(it.name, false) == null }.forEach { field ->
            addedElements += if (firstMethod != null) target.addBefore(field.copy(), firstMethod) else target.add(field.copy())
        }

        val hasSetUp = target.methods.any { method -> SET_UP_ANNOTATIONS.any { method.hasAnnotation(it) } }
        var addedTests = 0
        source.methods
            .filter { target.findMethodsByName(it.name, false).isEmpty() }
            .filter { it.name in testMethodNames || !hasSetUp }
            .forEach { method ->
                addedElements += target.add(method.copy())
                if (method.name in testMethodNames) addedTests++
            }

        val styleManager = CodeStyleManager.getInstance(project)
        addedElements.filter { it.isValid }.forEach { styleManager.reformat(it) }
        return addedTests
    }

    private fun pickClosest(candidates: List<VirtualFile>, sourceRoot: VirtualFile): VirtualFile? =
        candidates.maxWithOrNull(
            compareBy<VirtualFile>({ commonSegments(it, sourceRoot) }, { if (it.name == sourceRoot.name) 1 else 0 }),
        )

    private fun segments(file: VirtualFile) = file.path.split('/').filter { it.isNotEmpty() }

    private fun commonSegments(a: VirtualFile, b: VirtualFile): Int =
        segments(a).zip(segments(b)).takeWhile { (x, y) -> x == y }.size

    companion object {
        private val SET_UP_ANNOTATIONS = listOf("org.junit.jupiter.api.BeforeEach", "org.junit.Before")
    }
}
