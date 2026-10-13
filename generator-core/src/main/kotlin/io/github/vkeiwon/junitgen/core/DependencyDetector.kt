package io.github.vkeiwon.junitgen.core

import io.github.vkeiwon.junitgen.core.model.FieldModel
import io.github.vkeiwon.junitgen.core.model.TypeKind
import io.github.vkeiwon.junitgen.core.model.TypeRef

/** 필드 중 Mock 으로 대체할 협력 객체(의존성)를 골라낸다. */
object DependencyDetector {

    /** 의존성이 아니라 "값"으로 취급하는 JDK 타입들. */
    private val VALUE_TYPES = setOf(
        "java.lang.Object", "java.lang.String", "java.lang.CharSequence", "java.lang.Number",
        "java.lang.Integer", "java.lang.Long", "java.lang.Double", "java.lang.Float",
        "java.lang.Short", "java.lang.Byte", "java.lang.Character", "java.lang.Boolean",
        "java.lang.Iterable", "java.util.Collection", "java.util.List", "java.util.Set",
        "java.util.SortedSet", "java.util.Queue", "java.util.Deque", "java.util.Map",
        "java.util.SortedMap", "java.util.Optional", "java.util.stream.Stream",
        "java.time.temporal.Temporal", "java.time.temporal.TemporalAmount",
    )

    fun detect(fields: List<FieldModel>): List<FieldModel> = fields.filter(::isDependency)

    fun isDependency(field: FieldModel): Boolean {
        if (field.isStatic || !isMockable(field.type)) return false
        if (field.isInjected) return true
        // 선언과 동시에 초기화되는 필드(new ObjectMapper() 등)는 외부에서 주입되지 않는다.
        return !field.hasInitializer
    }

    fun isMockable(type: TypeRef): Boolean {
        if (type.isArray) return false
        if (type.kind !in setOf(TypeKind.CLASS, TypeKind.ABSTRACT_CLASS, TypeKind.INTERFACE)) return false
        if (type.qualifiedName in VALUE_TYPES) return false
        val isJdk = type.qualifiedName.startsWith("java.") || type.qualifiedName.startsWith("javax.")
        // JDK 구체 클래스(String, ArrayList, StringBuilder ...)는 값으로 본다. Clock, DataSource 같은 추상 타입은 의존성.
        return !(isJdk && type.kind == TypeKind.CLASS)
    }
}
