package io.github.vkeiwon.junitgen.core

import io.github.vkeiwon.junitgen.core.model.TypeKind
import io.github.vkeiwon.junitgen.core.model.TypeRef

/**
 * 생성 코드에서 사용하는 타입의 import 를 모으고, 코드에 쓸 이름(단순 이름 또는 FQN)을 결정한다.
 * 단순 이름이 겹치면 먼저 등록된 쪽이 단순 이름을 갖고 나머지는 FQN 으로 출력한다.
 */
class ImportCollector(private val currentPackage: String, reservedSimpleNames: Collection<String> = emptyList()) {

    private val simpleNameOwners = mutableMapOf<String, String>()
    private val imports = sortedSetOf<String>()
    private val staticImports = sortedSetOf<String>()

    init {
        reservedSimpleNames.forEach { simpleNameOwners[it] = qualify(it) }
    }

    private fun qualify(simpleName: String) = if (currentPackage.isEmpty()) simpleName else "$currentPackage.$simpleName"

    /** [qualifiedName] 을 사용 등록하고 코드에 쓸 이름을 돌려준다. */
    fun use(qualifiedName: String): String {
        if ('.' !in qualifiedName) return qualifiedName
        val simple = qualifiedName.substringAfterLast('.')
        val owner = simpleNameOwners[simple]
        if (owner != null) return if (owner == qualifiedName) simple else qualifiedName
        simpleNameOwners[simple] = qualifiedName
        val container = qualifiedName.substringBeforeLast('.')
        if (container != "java.lang" && container != currentPackage) imports += qualifiedName
        return simple
    }

    /** 정적 멤버를 static import 로 등록하고 멤버 이름을 돌려준다. */
    fun useStatic(ownerQualifiedName: String, member: String): String {
        staticImports += "$ownerQualifiedName.$member"
        return member
    }

    /** 변수 선언 등에 쓰는 타입 표기. */
    fun typeText(type: TypeRef): String {
        val base = when {
            type.kind == TypeKind.WILDCARD -> when (val bound = type.wildcardBound) {
                null -> "?"
                else -> (if (type.wildcardSuper) "? super " else "? extends ") + typeText(bound)
            }
            type.kind == TypeKind.PRIMITIVE -> type.qualifiedName
            else -> {
                val name = use(type.qualifiedName)
                if (type.typeArguments.isEmpty()) name else type.typeArguments.joinToString(", ", "$name<", ">") { typeText(it) }
            }
        }
        return base + "[]".repeat(type.arrayDimensions)
    }

    /** `Foo.class`, `int[].class` 처럼 클래스 리터럴/배열 생성에 쓰는 소거(erasure) 표기. */
    fun rawTypeText(type: TypeRef): String {
        val base = if (type.kind == TypeKind.PRIMITIVE) type.qualifiedName else use(type.qualifiedName)
        return base + "[]".repeat(type.arrayDimensions)
    }

    fun render(): String = buildString {
        imports.forEach { append("import ").append(it).append(";\n") }
        if (imports.isNotEmpty() && staticImports.isNotEmpty()) append('\n')
        staticImports.forEach { append("import static ").append(it).append(";\n") }
    }
}
