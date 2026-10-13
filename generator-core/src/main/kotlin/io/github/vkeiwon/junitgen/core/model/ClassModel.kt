package io.github.vkeiwon.junitgen.core.model

enum class ClassKind { CLASS, ABSTRACT_CLASS, RECORD }

data class ParameterModel(val name: String, val type: TypeRef)

data class ConstructorModel(
    val parameters: List<ParameterModel> = emptyList(),
    val declaresExceptions: Boolean = false,
)

data class FieldModel(
    val name: String,
    val type: TypeRef,
    val isStatic: Boolean = false,
    val hasInitializer: Boolean = false,
    /** @Autowired / @Inject / @Resource 등으로 주입되는 필드. */
    val isInjected: Boolean = false,
)

/** 테스트 대상 메서드가 의존성(필드) 객체에 대해 호출하는 메서드. Mock stub/verify 생성에 사용한다. */
data class DependencyCall(
    val fieldName: String,
    val methodName: String,
    val parameterTypes: List<TypeRef>,
    val returnType: TypeRef,
    /** 같은 이름·같은 인자 개수의 오버로드가 있어 `any()` 만으로는 모호한 경우. */
    val isOverloaded: Boolean = false,
)

data class MethodModel(
    val name: String,
    val returnType: TypeRef = TypeRef.VOID,
    val parameters: List<ParameterModel> = emptyList(),
    val isStatic: Boolean = false,
    val isAbstract: Boolean = false,
    /** 단순 getter/setter 여부. */
    val isAccessor: Boolean = false,
    val thrownExceptions: List<TypeRef> = emptyList(),
    val dependencyCalls: List<DependencyCall> = emptyList(),
)

data class ClassModel(
    val packageName: String,
    val simpleName: String,
    val kind: ClassKind = ClassKind.CLASS,
    /** private 이 아닌 생성자들. 비어 있고 [hasOnlyPrivateConstructors] 가 false 면 기본 생성자가 있는 것으로 본다. */
    val constructors: List<ConstructorModel> = emptyList(),
    val hasOnlyPrivateConstructors: Boolean = false,
    val fields: List<FieldModel> = emptyList(),
    /** private 이 아닌 메서드들. */
    val methods: List<MethodModel> = emptyList(),
) {
    val qualifiedName: String get() = if (packageName.isEmpty()) simpleName else "$packageName.$simpleName"
}
