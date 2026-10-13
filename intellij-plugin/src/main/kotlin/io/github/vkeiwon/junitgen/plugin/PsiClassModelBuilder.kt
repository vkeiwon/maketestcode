package io.github.vkeiwon.junitgen.plugin

import com.intellij.psi.JavaRecursiveElementWalkingVisitor
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiEnumConstant
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiThisExpression
import com.intellij.psi.impl.light.LightElement
import com.intellij.psi.util.PropertyUtilBase
import com.intellij.psi.util.PsiUtil
import io.github.vkeiwon.junitgen.core.DependencyDetector
import io.github.vkeiwon.junitgen.core.model.ClassKind
import io.github.vkeiwon.junitgen.core.model.ClassModel
import io.github.vkeiwon.junitgen.core.model.ConstructorModel
import io.github.vkeiwon.junitgen.core.model.DependencyCall
import io.github.vkeiwon.junitgen.core.model.FieldModel
import io.github.vkeiwon.junitgen.core.model.MethodModel
import io.github.vkeiwon.junitgen.core.model.ParameterModel
import io.github.vkeiwon.junitgen.core.model.TypeKind
import io.github.vkeiwon.junitgen.plugin.PsiTypeConverter.convert

/** [PsiClass] 를 분석해 생성기가 사용하는 [ClassModel] 을 만든다. read action 안에서 호출해야 한다. */
object PsiClassModelBuilder {

    private val INJECT_ANNOTATIONS = listOf(
        "org.springframework.beans.factory.annotation.Autowired",
        "javax.inject.Inject",
        "jakarta.inject.Inject",
        "javax.annotation.Resource",
        "jakarta.annotation.Resource",
    )

    /** 테스트를 생성할 수 없는 클래스면 그 이유를, 가능하면 null 을 돌려준다. */
    fun unsupportedReason(psiClass: PsiClass): String? = when {
        psiClass.qualifiedName == null || psiClass.name == null -> "익명/로컬 클래스는 지원하지 않습니다."
        psiClass.isAnnotationType -> "애노테이션 타입은 테스트 대상이 아닙니다."
        psiClass.isInterface -> "인터페이스는 테스트 대상이 아닙니다. 구현 클래스에서 실행하세요."
        psiClass.isEnum -> "enum 은 지원하지 않습니다."
        else -> null
    }

    fun build(psiClass: PsiClass): ClassModel {
        val packageName = (psiClass.containingFile as? PsiClassOwner)?.packageName.orEmpty()

        val fields = psiClass.fields
            .filter { it !is PsiEnumConstant }
            .map { field ->
                FieldModel(
                    name = field.name,
                    type = convert(field.type),
                    isStatic = field.hasModifierProperty(PsiModifier.STATIC),
                    hasInitializer = field.hasInitializer(),
                    isInjected = INJECT_ANNOTATIONS.any { field.hasAnnotation(it) },
                )
            }
        val dependencyNames = DependencyDetector.detect(fields).map { it.name }.toSet()

        val nonPrivateConstructors = psiClass.constructors.filter { !it.hasModifierProperty(PsiModifier.PRIVATE) }
        val constructors = nonPrivateConstructors.map { ctor ->
            ConstructorModel(
                parameters = ctor.parameterList.parameters.map { ParameterModel(it.name, convert(it.type)) },
                declaresExceptions = ctor.throwsList.referencedTypes.isNotEmpty(),
            )
        }.toMutableList()
        if (psiClass.isRecord) {
            // 레코드의 정식(canonical) 생성자는 암묵적으로 존재할 수 있다.
            constructors += ConstructorModel(psiClass.recordComponents.map { ParameterModel(it.name, convert(it.type)) })
        }

        val methods = psiClass.methods
            .filter { !it.isConstructor && !it.hasModifierProperty(PsiModifier.PRIVATE) && it !is LightElement }
            .map { method -> buildMethod(psiClass, method, dependencyNames) }

        return ClassModel(
            packageName = packageName,
            simpleName = psiClass.name!!,
            kind = when (PsiTypeConverter.kindOf(psiClass)) {
                TypeKind.ABSTRACT_CLASS -> ClassKind.ABSTRACT_CLASS
                TypeKind.RECORD -> ClassKind.RECORD
                else -> ClassKind.CLASS
            },
            constructors = constructors,
            hasOnlyPrivateConstructors = psiClass.constructors.isNotEmpty() && nonPrivateConstructors.isEmpty() && !psiClass.isRecord,
            fields = fields,
            methods = methods,
        )
    }

    private fun buildMethod(owner: PsiClass, method: PsiMethod, dependencyNames: Set<String>) = MethodModel(
        name = method.name,
        returnType = convert(method.returnType),
        parameters = method.parameterList.parameters.map { ParameterModel(it.name, convert(it.type)) },
        isStatic = method.hasModifierProperty(PsiModifier.STATIC),
        isAbstract = method.hasModifierProperty(PsiModifier.ABSTRACT),
        isAccessor = PropertyUtilBase.isSimpleGetter(method) || PropertyUtilBase.isSimpleSetter(method),
        thrownExceptions = method.throwsList.referencedTypes.map { convert(it) },
        dependencyCalls = if (dependencyNames.isEmpty()) emptyList() else collectDependencyCalls(owner, method, dependencyNames),
    )

    /**
     * 메서드 본문에서 의존성 필드에 대한 호출(`repository.save(..)`)을 찾는다.
     * 같은 클래스의 다른 메서드(private 헬퍼 등)를 호출하면 그 본문도 따라가서 수집한다.
     */
    private fun collectDependencyCalls(owner: PsiClass, method: PsiMethod, dependencyNames: Set<String>): List<DependencyCall> {
        val calls = LinkedHashMap<String, DependencyCall>()
        val visited = HashSet<PsiMethod>()

        fun visit(target: PsiMethod) {
            if (!visited.add(target)) return
            val body = target.body ?: return
            body.accept(object : JavaRecursiveElementWalkingVisitor() {
                override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                    super.visitMethodCallExpression(expression)
                    val qualifier = PsiUtil.skipParenthesizedExprDown(expression.methodExpression.qualifierExpression)
                    if (qualifier == null || qualifier is PsiThisExpression) {
                        val helper = expression.resolveMethod()
                        if (helper != null && helper.containingClass == owner) visit(helper)
                        return
                    }
                    val field = (qualifier as? PsiReferenceExpression)?.resolve() as? PsiField ?: return
                    if (field.containingClass != owner || field.name !in dependencyNames) return

                    val resolved = expression.resolveMethodGenerics()
                    val called = resolved.element as? PsiMethod ?: return
                    val substitutor = resolved.substitutor
                    val parameterTypes = called.parameterList.parameters.map { convert(substitutor.substitute(it.type)) }
                    val arity = called.parameterList.parametersCount
                    val overloads = called.containingClass?.findMethodsByName(called.name, true)
                        ?.count { it.parameterList.parametersCount == arity } ?: 1
                    val call = DependencyCall(
                        fieldName = field.name,
                        methodName = called.name,
                        parameterTypes = parameterTypes,
                        returnType = convert(substitutor.substitute(called.returnType)),
                        isOverloaded = overloads > 1,
                    )
                    calls.putIfAbsent("${call.fieldName}.${call.methodName}${parameterTypes.map { it.qualifiedName }}", call)
                }
            })
        }

        visit(method)
        return calls.values.toList()
    }
}
