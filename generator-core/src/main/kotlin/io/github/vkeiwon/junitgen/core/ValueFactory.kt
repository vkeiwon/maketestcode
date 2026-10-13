package io.github.vkeiwon.junitgen.core

import io.github.vkeiwon.junitgen.core.model.TypeKind
import io.github.vkeiwon.junitgen.core.model.TypeRef

/** 타입별로 테스트에 넣을 기본 값 표현식(Java 소스)을 만든다. */
class ValueFactory(private val imports: ImportCollector, private val useMockito: Boolean) {

    fun valueFor(type: TypeRef, nameHint: String = "value", depth: Int = 0): String {
        if (type.isArray) {
            val dims = "[]".repeat(type.arrayDimensions - 1)
            return "new ${imports.rawTypeText(type.componentType())}[0]$dims"
        }
        if (type.kind == TypeKind.WILDCARD) {
            val bound = type.wildcardBound
            return if (bound == null || type.wildcardSuper) NULL else valueFor(bound, nameHint, depth)
        }
        primitiveOrBoxed(type.qualifiedName)?.let { return it }
        if (depth > MAX_DEPTH) return NULL

        when (type.qualifiedName) {
            "java.lang.String", "java.lang.CharSequence" -> return "\"$nameHint\""
            "java.lang.Number" -> return "1"
            "java.lang.Object" -> return "new Object()"
            "java.lang.Class" -> {
                val arg = type.typeArguments.firstOrNull()
                val literal = if (arg != null && arg.kind in CONCRETE_REFERENCE_KINDS) imports.rawTypeText(arg) else "Object"
                return "$literal.class"
            }
            "java.math.BigDecimal", "java.math.BigInteger" -> return "${imports.use(type.qualifiedName)}.ONE"
            "java.util.List", "java.util.Collection", "java.lang.Iterable" -> return factoryOf("java.util.List", type, nameHint, depth)
            "java.util.Set" -> return factoryOf("java.util.Set", type, nameHint, depth)
            "java.util.stream.Stream" -> return factoryOf("java.util.stream.Stream", type, nameHint, depth, emptyMethod = "empty")
            "java.util.Map" -> return "${imports.use("java.util.Map")}.of()"
            "java.util.Optional" -> {
                val element = elementValue(type, nameHint, depth)
                val optional = imports.use("java.util.Optional")
                return if (element == null) "$optional.empty()" else "$optional.of($element)"
            }
            "java.util.UUID" -> return "${imports.use("java.util.UUID")}.fromString(\"00000000-0000-0000-0000-000000000001\")"
            "java.util.Date" -> return "new ${imports.use("java.util.Date")}()"
            "java.time.LocalDate" -> return "${imports.use(type.qualifiedName)}.of(2024, 1, 1)"
            "java.time.LocalDateTime" -> return "${imports.use(type.qualifiedName)}.of(2024, 1, 1, 0, 0)"
            "java.time.LocalTime" -> return "${imports.use(type.qualifiedName)}.of(0, 0)"
            "java.time.Instant" -> return "${imports.use(type.qualifiedName)}.EPOCH"
            "java.time.Duration" -> return "${imports.use(type.qualifiedName)}.ofSeconds(1)"
        }

        return when {
            type.kind == TypeKind.ENUM ->
                type.enumConstants.firstOrNull()?.let { "${imports.use(type.qualifiedName)}.$it" } ?: NULL
            type.kind == TypeKind.CLASS && type.hasAccessibleNoArgConstructor ->
                "new ${imports.use(type.qualifiedName)}${if (type.typeArguments.isEmpty()) "" else "<>"}()"
            useMockito && type.kind in MOCKABLE_KINDS -> mockOf(type)
            else -> NULL
        }
    }

    fun mockOf(type: TypeRef): String =
        "${imports.useStatic("org.mockito.Mockito", "mock")}(${imports.rawTypeText(type)}.class)"

    /** Mockito 인자 매처. 오버로드로 모호할 때는 `any(Type.class)` 를 쓴다. */
    fun matcherFor(type: TypeRef, disambiguate: Boolean): String {
        val matchers = "org.mockito.ArgumentMatchers"
        if (type.isPrimitive) {
            val name = "any" + type.qualifiedName.replaceFirstChar { it.uppercaseChar() }
            return "${imports.useStatic(matchers, name)}()"
        }
        val any = imports.useStatic(matchers, "any")
        return if (disambiguate) "$any(${imports.rawTypeText(type)}.class)" else "$any()"
    }

    private fun elementValue(type: TypeRef, nameHint: String, depth: Int): String? {
        val arg = type.typeArguments.firstOrNull() ?: return null
        return valueFor(arg, nameHint, depth + 1).takeUnless { it == NULL }
    }

    private fun factoryOf(owner: String, type: TypeRef, nameHint: String, depth: Int, emptyMethod: String = "of"): String {
        val element = elementValue(type, nameHint, depth)
        val name = imports.use(owner)
        return if (element == null) "$name.$emptyMethod()" else "$name.of($element)"
    }

    private fun primitiveOrBoxed(qualifiedName: String): String? = when (qualifiedName) {
        "int", "java.lang.Integer" -> "1"
        "long", "java.lang.Long" -> "1L"
        "double", "java.lang.Double" -> "1.0"
        "float", "java.lang.Float" -> "1.0f"
        "short", "java.lang.Short" -> "(short) 1"
        "byte", "java.lang.Byte" -> "(byte) 1"
        "char", "java.lang.Character" -> "'a'"
        "boolean", "java.lang.Boolean" -> "true"
        else -> null
    }

    companion object {
        const val NULL = "null"
        private const val MAX_DEPTH = 3
        private val MOCKABLE_KINDS = setOf(TypeKind.CLASS, TypeKind.ABSTRACT_CLASS, TypeKind.INTERFACE)
        private val CONCRETE_REFERENCE_KINDS = setOf(
            TypeKind.CLASS, TypeKind.ABSTRACT_CLASS, TypeKind.INTERFACE, TypeKind.ENUM, TypeKind.RECORD, TypeKind.ANNOTATION,
        )
    }
}
