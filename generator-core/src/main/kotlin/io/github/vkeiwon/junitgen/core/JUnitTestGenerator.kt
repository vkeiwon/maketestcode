package io.github.vkeiwon.junitgen.core

import io.github.vkeiwon.junitgen.core.model.ClassKind
import io.github.vkeiwon.junitgen.core.model.ClassModel
import io.github.vkeiwon.junitgen.core.model.FieldModel
import io.github.vkeiwon.junitgen.core.model.MethodModel
import io.github.vkeiwon.junitgen.core.model.TypeRef

data class GeneratedTest(
    val packageName: String,
    val className: String,
    val source: String,
    val testMethodNames: List<String>,
) {
    val fileName: String get() = "$className.java"
}

/**
 * [ClassModel] 로부터 규칙/템플릿 기반 JUnit 테스트 소스를 생성한다. 네트워크나 LLM 없이 결정적으로 동작한다.
 *
 * - public/protected/package-private 메서드마다 given/when/then 형태의 성공 케이스 테스트
 * - throws 절의 예외마다 비활성화(@Disabled)된 예외 테스트 골격
 * - 협력 객체 필드는 @Mock 으로, 메서드가 호출하는 의존성 메서드는 when(...).thenReturn(...) / verify(...) 로 생성
 */
class JUnitTestGenerator(private val options: GeneratorOptions = GeneratorOptions()) {

    fun testClassName(model: ClassModel): String = model.simpleName + options.testClassSuffix

    fun generate(model: ClassModel): GeneratedTest = Builder(model).build()

    private sealed interface SutInit {
        data object None : SutInit
        data object InjectMocks : SutInit
        data class Expression(val text: String, val declaresExceptions: Boolean = false, val todo: String? = null) : SutInit
    }

    private inner class Builder(private val model: ClassModel) {
        private val testClassName = testClassName(model)
        private val imports = ImportCollector(model.packageName, listOf(testClassName))
        private val values = ValueFactory(imports, options.useMockito)
        private val junit5 = options.junitVersion == JUnitVersion.JUNIT5
        private val assertj = options.assertionStyle == AssertionStyle.ASSERTJ
        private val memberPrefix = if (junit5) "" else "public "

        private val methods = model.methods.filter { !it.isAbstract && !(options.skipAccessors && it.isAccessor) }
        private val sutType = imports.use(model.qualifiedName)
        private val needsInstance = methods.any { !it.isStatic }
        private val mocks: List<FieldModel> =
            if (options.useMockito && needsInstance && model.kind != ClassKind.ABSTRACT_CLASS) DependencyDetector.detect(model.fields)
            else emptyList()
        private val mockNames = mocks.map { it.name }.toSet()
        private val sutName = decapitalize(model.simpleName).let { if (it in mockNames || it in JAVA_KEYWORDS) "sut" else it }
        private val usedTestNames = mutableSetOf<String>()

        fun build(): GeneratedTest {
            val sutInit = sutInit()
            val body = CodeWriter()
            val testNames = mutableListOf<String>()

            mocks.forEach { field ->
                body.line("@${imports.use("org.mockito.Mock")}")
                body.line("private ${imports.typeText(field.type)} ${field.name};")
                body.blank()
            }
            if (sutInit != SutInit.None) {
                if (sutInit == SutInit.InjectMocks) body.line("@${imports.use("org.mockito.InjectMocks")}")
                body.line("private $sutType $sutName;")
                body.blank()
            }
            if (sutInit is SutInit.Expression) {
                body.line("@${imports.use(if (junit5) "org.junit.jupiter.api.BeforeEach" else "org.junit.Before")}")
                body.block("${memberPrefix}void setUp()${if (sutInit.declaresExceptions) " throws Exception" else ""}") {
                    sutInit.todo?.let { line("// TODO: $it") }
                    line("$sutName = ${sutInit.text};")
                }
                body.blank()
            }

            methods.forEach { method ->
                testNames += successTest(method, body)
                if (options.generateExceptionTests) {
                    method.thrownExceptions.distinctBy { it.qualifiedName }.forEach { exception ->
                        testNames += exceptionTest(method, exception, body)
                    }
                }
            }
            if (methods.isEmpty()) {
                body.line("// TODO: 테스트할 메서드가 없습니다 (private 이 아닌 메서드가 없거나 모두 getter/setter 입니다).")
                body.blank()
            }

            val classAnnotation = when {
                mocks.isEmpty() -> null
                junit5 -> "@${imports.use("org.junit.jupiter.api.extension.ExtendWith")}(${imports.use("org.mockito.junit.jupiter.MockitoExtension")}.class)"
                else -> "@${imports.use("org.junit.runner.RunWith")}(${imports.use("org.mockito.junit.MockitoJUnitRunner")}.class)"
            }

            val source = buildString {
                if (model.packageName.isNotEmpty()) append("package ").append(model.packageName).append(";\n\n")
                val importBlock = imports.render()
                if (importBlock.isNotEmpty()) append(importBlock).append('\n')
                classAnnotation?.let { append(it).append('\n') }
                append(memberPrefix).append("class ").append(testClassName).append(" {\n\n")
                append(body.render(indent = 1).trimEnd()).append('\n')
                append("}\n")
            }
            return GeneratedTest(model.packageName, testClassName, source, testNames)
        }

        private fun sutInit(): SutInit {
            if (!needsInstance) return SutInit.None
            if (model.kind == ClassKind.ABSTRACT_CLASS) {
                return if (options.useMockito) {
                    val mock = imports.useStatic("org.mockito.Mockito", "mock")
                    val callsReal = imports.useStatic("org.mockito.Mockito", "CALLS_REAL_METHODS")
                    SutInit.Expression("$mock($sutType.class, $callsReal)")
                } else {
                    SutInit.Expression(ValueFactory.NULL, todo = "추상 클래스입니다. 테스트용 하위 클래스를 만들어 생성하세요.")
                }
            }
            if (model.hasOnlyPrivateConstructors) {
                return SutInit.Expression(ValueFactory.NULL, todo = "private 생성자만 있습니다. 인스턴스를 얻는 방법을 지정하세요.")
            }
            val widest = model.constructors.maxByOrNull { it.parameters.size }
            if (mocks.isNotEmpty() && (widest == null || widest.parameters.isEmpty())) {
                // 필드 주입(@Autowired 필드 등) → Mockito 가 주입하도록 맡긴다.
                return SutInit.InjectMocks
            }
            val hasNoArg = model.constructors.isEmpty() || model.constructors.any { it.parameters.isEmpty() }
            val ctor = if (mocks.isEmpty() && hasNoArg) null else widest
            if (ctor == null) return SutInit.Expression("new $sutType()")

            val unusedMocks = mocks.toMutableList()
            val args = ctor.parameters.map { param ->
                val mock = unusedMocks.firstOrNull { it.name == param.name && it.type.sameTypeAs(param.type) }
                    ?: unusedMocks.firstOrNull { it.type.sameTypeAs(param.type) }
                if (mock != null) {
                    unusedMocks -= mock
                    mock.name
                } else {
                    values.valueFor(param.type, param.name)
                }
            }
            val todo = if (ValueFactory.NULL in args) "null 로 채운 생성자 인자를 실제 객체나 테스트 더블로 바꾸세요." else null
            return SutInit.Expression("new $sutType(${args.joinToString(", ")})", ctor.declaresExceptions, todo)
        }

        private fun successTest(method: MethodModel, out: CodeWriter): String {
            val testName = uniqueTestName("${method.name}${overloadSuffix(method)}_success")
            val scope = LocalScope(mockNames + sutName)
            val locals = method.parameters.map { scope.claim(it.name) }
            val call = callExpression(method, locals)

            val given = method.parameters.zip(locals).map { (param, local) ->
                "${imports.typeText(param.type)} $local = ${values.valueFor(param.type, param.name)};"
            }
            val stubs = if (method.isStatic) emptyList() else stubLines(method)
            val verifies = if (method.isStatic) emptyList() else verifyLines(method)

            var callInLambda = false
            val whenThen = CodeWriter()
            when {
                !method.returnType.isVoid -> {
                    val result = scope.claim("result")
                    whenThen.line("// when")
                    whenThen.line("${imports.typeText(method.returnType)} $result = $call;")
                    whenThen.blank()
                    whenThen.line("// then")
                    if (method.returnType.isPrimitive) {
                        whenThen.line(if (assertj) "// TODO: assertThat($result).isEqualTo(expected);" else "// TODO: assertEquals(expected, $result);")
                    } else {
                        whenThen.line(assertNotNull(result))
                    }
                    verifies.forEach(whenThen::line)
                }
                verifies.isNotEmpty() -> {
                    whenThen.line("// when")
                    whenThen.line("$call;")
                    whenThen.blank()
                    whenThen.line("// then")
                    verifies.forEach(whenThen::line)
                }
                junit5 || assertj -> {
                    callInLambda = true
                    whenThen.line("// when & then")
                    whenThen.line(assertDoesNotThrow(call))
                }
                else -> {
                    whenThen.line("// when")
                    whenThen.line("$call;")
                    whenThen.blank()
                    whenThen.line("// then")
                    whenThen.line("// TODO: 실행 결과(상태 변화 등)를 검증하세요")
                }
            }

            val throwsClause = if (method.thrownExceptions.isNotEmpty() && !callInLambda) " throws Exception" else ""
            testAnnotations(out, displayName = "${signature(method)} - 정상 동작", disabledReason = null)
            out.block("${memberPrefix}void $testName()$throwsClause") {
                if (given.isNotEmpty() || stubs.isNotEmpty()) {
                    line("// given")
                    given.forEach(::line)
                    stubs.forEach(::line)
                    blank()
                }
                append(whenThen)
            }
            out.blank()
            return testName
        }

        private fun exceptionTest(method: MethodModel, exception: TypeRef, out: CodeWriter): String {
            val testName = uniqueTestName("${method.name}${overloadSuffix(method)}_throws${exception.simpleName}")
            val scope = LocalScope(mockNames + sutName)
            val locals = method.parameters.map { scope.claim(it.name) }
            val call = callExpression(method, locals)
            val exceptionType = imports.rawTypeText(exception)

            testAnnotations(
                out,
                displayName = "${signature(method)} - ${exception.simpleName} 발생",
                disabledReason = "TODO: 예외가 발생하는 조건을 설정한 뒤 활성화하세요",
            )
            out.block("${memberPrefix}void $testName()") {
                line("// given")
                method.parameters.zip(locals).forEach { (param, local) ->
                    line("${imports.typeText(param.type)} $local = ${values.valueFor(param.type, param.name)};")
                }
                line("// TODO: ${exception.simpleName} 이(가) 발생하도록 입력값 또는 Mock 동작을 설정하세요")
                blank()
                line("// when & then")
                if (assertj) {
                    val thrownBy = imports.useStatic(ASSERTJ, "assertThatThrownBy")
                    line("$thrownBy(() -> $call).isInstanceOf($exceptionType.class);")
                } else {
                    val assertThrows = imports.useStatic(junitAssertions(), "assertThrows")
                    line("$assertThrows($exceptionType.class, () -> $call);")
                }
            }
            out.blank()
            return testName
        }

        private fun testAnnotations(out: CodeWriter, displayName: String, disabledReason: String?) {
            out.line("@${imports.use(if (junit5) "org.junit.jupiter.api.Test" else "org.junit.Test")}")
            if (disabledReason != null) {
                val disabled = imports.use(if (junit5) "org.junit.jupiter.api.Disabled" else "org.junit.Ignore")
                out.line("@$disabled(\"$disabledReason\")")
            }
            if (junit5 && options.generateDisplayNames) {
                out.line("@${imports.use("org.junit.jupiter.api.DisplayName")}(\"$displayName\")")
            }
        }

        private fun stubLines(method: MethodModel): List<String> =
            method.dependencyCalls
                .filter { it.fieldName in mockNames && !it.returnType.isVoid }
                .mapNotNull { call ->
                    val value = values.valueFor(call.returnType, call.methodName)
                    if (value == ValueFactory.NULL) return@mapNotNull null
                    val whenFn = imports.useStatic("org.mockito.Mockito", "when")
                    "$whenFn(${call.fieldName}.${call.methodName}(${matchers(call.parameterTypes, call.isOverloaded)})).thenReturn($value);"
                }
                .distinct()

        private fun verifyLines(method: MethodModel): List<String> =
            method.dependencyCalls
                .filter { it.fieldName in mockNames && it.returnType.isVoid }
                .map { call ->
                    val verify = imports.useStatic("org.mockito.Mockito", "verify")
                    "$verify(${call.fieldName}).${call.methodName}(${matchers(call.parameterTypes, call.isOverloaded)});"
                }
                .distinct()

        private fun matchers(types: List<TypeRef>, disambiguate: Boolean) =
            types.joinToString(", ") { values.matcherFor(it, disambiguate) }

        private fun callExpression(method: MethodModel, args: List<String>): String {
            val target = if (method.isStatic) sutType else sutName
            return "$target.${method.name}(${args.joinToString(", ")})"
        }

        private fun assertNotNull(variable: String): String =
            if (assertj) "${imports.useStatic(ASSERTJ, "assertThat")}($variable).isNotNull();"
            else "${imports.useStatic(junitAssertions(), "assertNotNull")}($variable);"

        private fun assertDoesNotThrow(call: String): String =
            if (assertj) "${imports.useStatic(ASSERTJ, "assertThatCode")}(() -> $call).doesNotThrowAnyException();"
            else "${imports.useStatic(junitAssertions(), "assertDoesNotThrow")}(() -> $call);"

        private fun junitAssertions() = if (junit5) "org.junit.jupiter.api.Assertions" else "org.junit.Assert"

        private fun overloadSuffix(method: MethodModel): String {
            if (methods.count { it.name == method.name } < 2) return ""
            if (method.parameters.isEmpty()) return "_noArgs"
            return method.parameters.joinToString("") { param ->
                "_" + param.type.simpleName + "Array".repeat(param.type.arrayDimensions)
            }
        }

        private fun signature(method: MethodModel): String =
            method.parameters.joinToString(", ", "${method.name}(", ")") { param ->
                param.type.simpleName + "[]".repeat(param.type.arrayDimensions)
            }

        private fun uniqueTestName(base: String): String {
            var name = base
            var counter = 2
            while (!usedTestNames.add(name)) name = "$base${counter++}"
            return name
        }
    }

    /** 테스트 메서드 안의 지역 변수 이름이 필드나 서로와 겹치지 않도록 관리한다. */
    private class LocalScope(reserved: Set<String>) {
        private val taken = reserved.toMutableSet()

        fun claim(preferred: String): String {
            val base = if (preferred in JAVA_KEYWORDS) "${preferred}Value" else preferred
            if (taken.add(base)) return base
            if (base == "result" && taken.add("actual")) return "actual"
            var counter = 2
            while (!taken.add("$base$counter")) counter++
            return "$base$counter"
        }
    }

    companion object {
        private const val ASSERTJ = "org.assertj.core.api.Assertions"

        private val JAVA_KEYWORDS = setOf(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const", "continue",
            "default", "do", "double", "else", "enum", "extends", "final", "finally", "float", "for", "goto", "if",
            "implements", "import", "instanceof", "int", "interface", "long", "native", "new", "package", "private",
            "protected", "public", "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
            "throw", "throws", "transient", "try", "void", "volatile", "while", "true", "false", "null",
        )

        /** "UserService" → "userService", "URLParser" → "urlParser", "URL" → "url". */
        fun decapitalize(name: String): String {
            val upperRun = name.takeWhile { it.isUpperCase() }.length
            return when {
                upperRun == 0 -> name
                upperRun == 1 || upperRun == name.length -> name.substring(0, upperRun).lowercase() + name.substring(upperRun)
                else -> name.substring(0, upperRun - 1).lowercase() + name.substring(upperRun - 1)
            }
        }
    }
}
