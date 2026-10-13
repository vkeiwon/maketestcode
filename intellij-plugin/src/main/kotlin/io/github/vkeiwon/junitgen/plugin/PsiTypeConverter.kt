package io.github.vkeiwon.junitgen.plugin

import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiCapturedWildcardType
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiEnumConstant
import com.intellij.psi.PsiIntersectionType
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeParameter
import com.intellij.psi.PsiTypes
import com.intellij.psi.PsiWildcardType
import com.intellij.psi.util.TypeConversionUtil
import io.github.vkeiwon.junitgen.core.model.TypeKind
import io.github.vkeiwon.junitgen.core.model.TypeRef

/** PSI 타입을 IDE 독립적인 [TypeRef] 로 변환한다. 타입 변수는 소거(erasure)된 상한 타입으로 바꾼다. */
object PsiTypeConverter {

    private const val MAX_DEPTH = 4

    fun convert(type: PsiType?, depth: Int = 0): TypeRef = when (type) {
        null -> TypeRef.OBJECT
        PsiTypes.nullType() -> TypeRef.OBJECT
        is PsiPrimitiveType -> TypeRef.primitive(type.canonicalText)
        is PsiArrayType -> convert(type.deepComponentType, depth).copy(arrayDimensions = type.arrayDimensions)
        is PsiCapturedWildcardType -> convert(type.wildcard, depth)
        is PsiWildcardType -> TypeRef(
            "?",
            kind = TypeKind.WILDCARD,
            wildcardBound = type.bound?.let { convert(it, depth + 1) },
            wildcardSuper = type.isSuper,
        )
        is PsiIntersectionType -> convert(type.conjuncts.firstOrNull(), depth)
        is PsiClassType -> convertClassType(type, depth)
        else -> TypeRef.OBJECT
    }

    private fun convertClassType(type: PsiClassType, depth: Int): TypeRef {
        val psiClass = type.resolve() ?: return TypeRef(type.rawType().canonicalText)
        if (psiClass is PsiTypeParameter) {
            val erased = TypeConversionUtil.erasure(type)
            return if (erased is PsiClassType && erased.resolve() !is PsiTypeParameter) convert(erased, depth) else TypeRef.OBJECT
        }
        val qualifiedName = psiClass.qualifiedName ?: return TypeRef.OBJECT
        val arguments = if (depth >= MAX_DEPTH) emptyList() else type.parameters.map { convert(it, depth + 1) }
        val kind = kindOf(psiClass)
        return TypeRef(
            qualifiedName = qualifiedName,
            typeArguments = arguments,
            kind = kind,
            isFinal = psiClass.hasModifierProperty(PsiModifier.FINAL),
            hasAccessibleNoArgConstructor = kind == TypeKind.CLASS && hasAccessibleNoArgConstructor(psiClass),
            enumConstants = if (psiClass.isEnum) psiClass.fields.filterIsInstance<PsiEnumConstant>().map { it.name } else emptyList(),
        )
    }

    fun kindOf(psiClass: PsiClass): TypeKind = when {
        psiClass.isAnnotationType -> TypeKind.ANNOTATION
        psiClass.isInterface -> TypeKind.INTERFACE
        psiClass.isEnum -> TypeKind.ENUM
        psiClass.isRecord -> TypeKind.RECORD
        psiClass.hasModifierProperty(PsiModifier.ABSTRACT) -> TypeKind.ABSTRACT_CLASS
        else -> TypeKind.CLASS
    }

    /** 테스트 코드(다른 패키지일 수도 있음)에서 `new X()` 를 쓸 수 있는지. 보수적으로 public 만 인정한다. */
    private fun hasAccessibleNoArgConstructor(psiClass: PsiClass): Boolean {
        if (!psiClass.hasModifierProperty(PsiModifier.PUBLIC)) return false
        if (psiClass.containingClass != null && !psiClass.hasModifierProperty(PsiModifier.STATIC)) return false
        val constructors = psiClass.constructors
        if (constructors.isEmpty()) return true
        return constructors.any { it.parameterList.isEmpty && it.hasModifierProperty(PsiModifier.PUBLIC) }
    }
}
