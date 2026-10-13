package io.github.vkeiwon.junitgen.core

import io.github.vkeiwon.junitgen.core.model.TypeKind
import io.github.vkeiwon.junitgen.core.model.TypeRef
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ValueFactoryTest {

    private val imports = ImportCollector("x")
    private val withMockito = ValueFactory(imports, useMockito = true)
    private val withoutMockito = ValueFactory(imports, useMockito = false)

    @Test
    fun primitives() {
        assertEquals("1", withMockito.valueFor(TypeRef.primitive("int")))
        assertEquals("1L", withMockito.valueFor(TypeRef("java.lang.Long")))
        assertEquals("(short) 1", withMockito.valueFor(TypeRef.primitive("short")))
        assertEquals("'a'", withMockito.valueFor(TypeRef.primitive("char")))
        assertEquals("true", withMockito.valueFor(TypeRef.primitive("boolean")))
    }

    @Test
    fun `strings use the parameter name`() {
        assertEquals("\"email\"", withMockito.valueFor(TypeRef.STRING, "email"))
    }

    @Test
    fun `collections contain one element when possible`() {
        assertEquals("List.of(\"items\")", withMockito.valueFor(TypeRef.iface("java.util.List", TypeRef.STRING), "items"))
        assertEquals("Optional.empty()", withMockito.valueFor(TypeRef("java.util.Optional")))
        assertEquals("Map.of()", withMockito.valueFor(TypeRef.iface("java.util.Map", TypeRef.STRING, TypeRef.STRING)))
        assertEquals("new int[0]", withMockito.valueFor(TypeRef.primitive("int").copy(arrayDimensions = 1)))
        assertEquals("new String[0][]", withMockito.valueFor(TypeRef.STRING.copy(arrayDimensions = 2)))
    }

    @Test
    fun `objects are created, mocked or null`() {
        val pojo = TypeRef("x.Pojo", hasAccessibleNoArgConstructor = true)
        val service = TypeRef.iface("x.Service")
        val enumType = TypeRef("x.Color", kind = TypeKind.ENUM, enumConstants = listOf("RED"))
        assertEquals("new Pojo()", withMockito.valueFor(pojo))
        assertEquals("mock(Service.class)", withMockito.valueFor(service))
        assertEquals("null", withoutMockito.valueFor(service))
        assertEquals("Color.RED", withMockito.valueFor(enumType))
    }

    @Test
    fun matchers() {
        assertEquals("anyLong()", withMockito.matcherFor(TypeRef.primitive("long"), disambiguate = false))
        assertEquals("any()", withMockito.matcherFor(TypeRef.STRING, disambiguate = false))
        assertEquals("any(String.class)", withMockito.matcherFor(TypeRef.STRING, disambiguate = true))
    }
}
