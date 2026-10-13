package io.github.vkeiwon.junitgen.core.model

enum class TypeKind { PRIMITIVE, CLASS, ABSTRACT_CLASS, INTERFACE, ENUM, RECORD, ANNOTATION, WILDCARD }

/**
 * IDE(PSI)와 무관하게 Java 타입을 표현한다.
 *
 * 타입 변수(T 등)는 어댑터 단계에서 상한(bound)으로 치환되어 들어온다고 가정한다.
 */
data class TypeRef(
    /** "int", "java.lang.String", "com.foo.Outer.Inner" 처럼 정규화된 이름. 와일드카드는 "?". */
    val qualifiedName: String,
    val typeArguments: List<TypeRef> = emptyList(),
    val arrayDimensions: Int = 0,
    val kind: TypeKind = TypeKind.CLASS,
    val isFinal: Boolean = false,
    /** 테스트 코드에서 `new X()` 로 생성할 수 있는지 여부. */
    val hasAccessibleNoArgConstructor: Boolean = false,
    val enumConstants: List<String> = emptyList(),
    /** kind == WILDCARD 일 때 `? extends X` / `? super X` 의 X. null 이면 `?`. */
    val wildcardBound: TypeRef? = null,
    val wildcardSuper: Boolean = false,
) {
    val isArray: Boolean get() = arrayDimensions > 0
    val isPrimitive: Boolean get() = kind == TypeKind.PRIMITIVE && !isArray
    val isVoid: Boolean get() = qualifiedName == "void" && !isArray
    val simpleName: String get() = qualifiedName.substringAfterLast('.')

    fun componentType(): TypeRef = copy(arrayDimensions = 0)

    /** 제네릭 인자/배열 차원까지 비교하되 부가 정보(final 여부 등)는 무시한다. */
    fun sameTypeAs(other: TypeRef): Boolean =
        qualifiedName == other.qualifiedName &&
            arrayDimensions == other.arrayDimensions &&
            typeArguments.size == other.typeArguments.size &&
            typeArguments.zip(other.typeArguments).all { (a, b) -> a.sameTypeAs(b) }

    companion object {
        val VOID = primitive("void")
        val OBJECT = TypeRef("java.lang.Object", hasAccessibleNoArgConstructor = true)
        val STRING = TypeRef("java.lang.String", isFinal = true)

        fun primitive(name: String) = TypeRef(name, kind = TypeKind.PRIMITIVE, isFinal = true)

        fun of(qualifiedName: String, vararg typeArguments: TypeRef) =
            TypeRef(qualifiedName, typeArguments = typeArguments.toList())

        fun iface(qualifiedName: String, vararg typeArguments: TypeRef) =
            TypeRef(qualifiedName, typeArguments = typeArguments.toList(), kind = TypeKind.INTERFACE)
    }
}
