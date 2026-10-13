package io.github.vkeiwon.junitgen.core

import io.github.vkeiwon.junitgen.core.model.ClassKind
import io.github.vkeiwon.junitgen.core.model.ClassModel
import io.github.vkeiwon.junitgen.core.model.ConstructorModel
import io.github.vkeiwon.junitgen.core.model.DependencyCall
import io.github.vkeiwon.junitgen.core.model.FieldModel
import io.github.vkeiwon.junitgen.core.model.MethodModel
import io.github.vkeiwon.junitgen.core.model.ParameterModel
import io.github.vkeiwon.junitgen.core.model.TypeKind
import io.github.vkeiwon.junitgen.core.model.TypeRef

/**
 * src/test/resources/samples 의 Java 소스를 IntelliJ 어댑터(PsiClassModelBuilder)가 만들었을 모델로 손으로 옮긴 것.
 */
object SampleModels {
    private const val SHOP = "com.example.shop"

    val INT = TypeRef.primitive("int")
    val LONG = TypeRef.primitive("long")
    val DOUBLE = TypeRef.primitive("double")
    val BOOLEAN = TypeRef.primitive("boolean")
    val BOXED_LONG = TypeRef("java.lang.Long", isFinal = true)
    val STRING = TypeRef.STRING

    val USER = TypeRef("$SHOP.User", hasAccessibleNoArgConstructor = true)
    val GRADE = TypeRef("$SHOP.Grade", kind = TypeKind.ENUM, isFinal = true, enumConstants = listOf("BRONZE", "SILVER", "GOLD"))
    val USER_REPOSITORY = TypeRef.iface("$SHOP.UserRepository")
    val NOTIFIER = TypeRef.iface("$SHOP.Notifier")
    val USER_NOT_FOUND = TypeRef("$SHOP.UserNotFoundException")

    fun list(of: TypeRef) = TypeRef.iface("java.util.List", of)
    fun optional(of: TypeRef) = TypeRef("java.util.Optional", listOf(of), isFinal = true)

    val userService = ClassModel(
        packageName = SHOP,
        simpleName = "UserService",
        constructors = listOf(
            ConstructorModel(
                listOf(
                    ParameterModel("userRepository", USER_REPOSITORY),
                    ParameterModel("notifier", NOTIFIER),
                    ParameterModel("prefix", STRING),
                ),
            ),
        ),
        fields = listOf(
            FieldModel("userRepository", USER_REPOSITORY),
            FieldModel("notifier", NOTIFIER),
            FieldModel("prefix", STRING),
        ),
        methods = listOf(
            MethodModel(
                "getUser", USER, listOf(ParameterModel("id", BOXED_LONG)),
                thrownExceptions = listOf(USER_NOT_FOUND),
                dependencyCalls = listOf(DependencyCall("userRepository", "findById", listOf(BOXED_LONG), optional(USER))),
            ),
            MethodModel(
                "register", USER, listOf(ParameterModel("name", STRING), ParameterModel("grade", GRADE)),
                dependencyCalls = listOf(
                    DependencyCall("userRepository", "save", listOf(USER), USER),
                    DependencyCall("notifier", "send", listOf(USER), TypeRef.VOID, isOverloaded = true),
                ),
            ),
            MethodModel(
                "remove", TypeRef.VOID, listOf(ParameterModel("id", BOXED_LONG)),
                dependencyCalls = listOf(DependencyCall("userRepository", "deleteById", listOf(BOXED_LONG), TypeRef.VOID)),
            ),
            MethodModel(
                "countUsers", INT,
                dependencyCalls = listOf(DependencyCall("userRepository", "findAll", emptyList(), list(USER))),
            ),
            MethodModel(
                "names", list(STRING),
                listOf(ParameterModel("users", list(USER)), ParameterModel("limits", INT.copy(arrayDimensions = 1))),
            ),
            MethodModel("normalize", STRING, listOf(ParameterModel("name", STRING)), isStatic = true),
        ),
    )

    val legacyUserService = ClassModel(
        packageName = SHOP,
        simpleName = "LegacyUserService",
        fields = listOf(FieldModel("userRepository", USER_REPOSITORY)),
        methods = listOf(
            MethodModel(
                "find", USER, listOf(ParameterModel("id", BOXED_LONG)),
                dependencyCalls = listOf(DependencyCall("userRepository", "findById", listOf(BOXED_LONG), optional(USER))),
            ),
        ),
    )

    val abstractPolicy = ClassModel(
        packageName = SHOP,
        simpleName = "AbstractPolicy",
        kind = ClassKind.ABSTRACT_CLASS,
        methods = listOf(
            MethodModel("base", INT, isAbstract = true),
            MethodModel("describe", STRING, listOf(ParameterModel("grade", GRADE))),
        ),
    )

    val calculator = ClassModel(
        packageName = "com.example.calc",
        simpleName = "Calculator",
        fields = listOf(FieldModel("memory", INT)),
        methods = listOf(
            MethodModel("add", INT, listOf(ParameterModel("a", INT), ParameterModel("b", INT))),
            MethodModel("add", DOUBLE, listOf(ParameterModel("a", DOUBLE), ParameterModel("b", DOUBLE))),
            MethodModel(
                "divide", INT, listOf(ParameterModel("a", INT), ParameterModel("b", INT)),
                thrownExceptions = listOf(TypeRef("java.lang.ArithmeticException")),
            ),
            MethodModel(
                "scale", TypeRef("java.math.BigDecimal"),
                listOf(
                    ParameterModel("value", TypeRef("java.math.BigDecimal")),
                    ParameterModel("date", TypeRef("java.time.LocalDate", isFinal = true)),
                    ParameterModel("options", TypeRef.iface("java.util.Map", STRING, TypeRef("java.lang.Integer", isFinal = true))),
                ),
            ),
            MethodModel("reset"),
            MethodModel("isPositive", BOOLEAN, listOf(ParameterModel("value", LONG))),
        ),
    )

    val all = listOf(userService, legacyUserService, abstractPolicy, calculator)
}
