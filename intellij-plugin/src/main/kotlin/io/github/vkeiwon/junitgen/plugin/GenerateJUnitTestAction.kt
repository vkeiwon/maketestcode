package io.github.vkeiwon.junitgen.plugin

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtil
import io.github.vkeiwon.junitgen.core.GeneratedTest
import io.github.vkeiwon.junitgen.core.JUnitTestGenerator
import io.github.vkeiwon.junitgen.plugin.settings.JUnitGenSettings
import java.io.IOException

/**
 * 에디터(Alt+Insert → Generate)나 프로젝트 뷰(파일/패키지 다중 선택)에서 JUnit 테스트를 생성한다.
 * 네트워크나 LLM 을 사용하지 않는 규칙 기반 생성기다.
 */
class GenerateJUnitTestAction : AnAction() {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabledAndVisible = project != null && hasCandidates(e, project)
    }

    private fun hasCandidates(e: AnActionEvent, project: Project): Boolean {
        val index = ProjectFileIndex.getInstance(project)
        val editor = e.getData(CommonDataKeys.EDITOR)
        val psiFile = e.getData(CommonDataKeys.PSI_FILE)
        if (editor != null && psiFile != null) {
            val file = psiFile.virtualFile ?: return false
            return psiFile is PsiJavaFile && index.isInSourceContent(file) && !index.isInTestSourceContent(file)
        }
        val files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: return false
        return files.any { index.isInSourceContent(it) && (it.isDirectory || it.extension == "java") }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val classes = collectTargetClasses(e, project)
        if (classes.isEmpty()) {
            notify(project, "테스트를 생성할 Java 클래스를 찾지 못했습니다.", NotificationType.WARNING)
            return
        }

        val options = JUnitGenSettings.getInstance().toOptions()
        val generator = JUnitTestGenerator(options)
        val skipped = mutableListOf<String>()
        val jobs = classes.mapNotNull { psiClass ->
            val reason = PsiClassModelBuilder.unsupportedReason(psiClass)
            val sourceFile = psiClass.containingFile?.virtualFile
            if (reason != null || sourceFile == null) {
                skipped += "${psiClass.name}: ${reason ?: "소스 파일이 없습니다."}"
                null
            } else {
                sourceFile to generator.generate(PsiClassModelBuilder.build(psiClass))
            }
        }

        val writer = TestFileWriter(project)
        val results = mutableListOf<Pair<GeneratedTest, TestFileWriter.Result>>()
        val testRoots = linkedSetOf<VirtualFile>()
        WriteCommandAction.runWriteCommandAction(project, "Generate JUnit Tests", null, Runnable {
            for ((sourceFile, generated) in jobs) {
                try {
                    val testRoot = writer.findOrCreateTestRoot(sourceFile)
                    if (testRoot == null) {
                        skipped += "${generated.className}: 테스트 소스 루트를 찾을 수 없습니다 (src/test/java 를 테스트 소스 루트로 지정하세요)."
                        continue
                    }
                    testRoots += testRoot
                    results += generated to writer.write(generated, testRoot)
                } catch (ex: IOException) {
                    skipped += "${generated.className}: ${ex.message}"
                }
            }
        })

        report(project, results, skipped)
        results.singleOrNull()?.let { (_, result) ->
            result.file.virtualFile?.let { FileEditorManager.getInstance(project).openFile(it, true) }
        }
        val missing = testRoots.flatMap { TestLibraryChecker.missingLibraries(project, it, options) }.distinct()
        if (missing.isNotEmpty()) {
            notify(
                project,
                "테스트 클래스패스에 다음 라이브러리가 없어 생성된 테스트가 컴파일되지 않을 수 있습니다:<br/>" +
                    missing.joinToString("<br/>") { "• $it" },
                NotificationType.WARNING,
            )
        }
    }

    private fun collectTargetClasses(e: AnActionEvent, project: Project): List<PsiClass> {
        val editor = e.getData(CommonDataKeys.EDITOR)
        val psiFile = e.getData(CommonDataKeys.PSI_FILE)
        if (editor != null && psiFile is PsiJavaFile) {
            val element = psiFile.findElementAt(editor.caretModel.offset)
            val atCaret = PsiTreeUtil.getParentOfType(element, PsiClass::class.java, false)?.let { PsiUtil.getTopLevelClass(it) }
            return listOfNotNull(atCaret ?: psiFile.classes.firstOrNull())
        }

        val index = ProjectFileIndex.getInstance(project)
        val psiManager = PsiManager.getInstance(project)
        val result = LinkedHashSet<PsiClass>()
        e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)?.forEach { root ->
            VfsUtilCore.iterateChildrenRecursively(root, VirtualFileFilter { !index.isExcluded(it) }) { file ->
                if (!file.isDirectory && file.extension == "java" && index.isInSourceContent(file) && !index.isInTestSourceContent(file)) {
                    (psiManager.findFile(file) as? PsiJavaFile)?.classes
                        ?.filter { PsiClassModelBuilder.unsupportedReason(it) == null }
                        ?.let { result += it }
                }
                true
            }
        }
        return result.toList()
    }

    private fun report(project: Project, results: List<Pair<GeneratedTest, TestFileWriter.Result>>, skipped: List<String>) {
        val lines = results.map { (generated, result) ->
            if (result.created) "${generated.className} 생성 (테스트 ${result.addedTests}개)"
            else "${generated.className} 에 테스트 ${result.addedTests}개 추가"
        }
        if (lines.isNotEmpty()) notify(project, lines.joinToString("<br/>"), NotificationType.INFORMATION)
        if (skipped.isNotEmpty()) notify(project, "건너뜀:<br/>" + skipped.joinToString("<br/>"), NotificationType.WARNING)
    }

    private fun notify(project: Project, content: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP)
            .createNotification("JUnit Test Generator", content, type)
            .notify(project)
    }

    companion object {
        const val NOTIFICATION_GROUP = "JUnit Test Generator"
    }
}
