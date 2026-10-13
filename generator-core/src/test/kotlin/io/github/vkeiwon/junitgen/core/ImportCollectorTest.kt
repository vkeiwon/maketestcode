package io.github.vkeiwon.junitgen.core

import io.github.vkeiwon.junitgen.core.model.TypeKind
import io.github.vkeiwon.junitgen.core.model.TypeRef
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ImportCollectorTest {

    @Test
    fun `skips java lang and same package, imports the rest`() {
        val imports = ImportCollector("com.example")
        assertEquals("String", imports.use("java.lang.String"))
        assertEquals("Foo", imports.use("com.example.Foo"))
        assertEquals("List", imports.use("java.util.List"))
        assertEquals("Entry", imports.use("java.util.Map.Entry"))
        assertEquals("import java.util.List;\nimport java.util.Map.Entry;\n", imports.render())
    }

    @Test
    fun `conflicting simple names fall back to qualified names`() {
        val imports = ImportCollector("com.example", listOf("DateTest"))
        assertEquals("Date", imports.use("java.util.Date"))
        assertEquals("java.sql.Date", imports.use("java.sql.Date"))
        assertEquals("com.other.DateTest", imports.use("com.other.DateTest"))
    }

    @Test
    fun `renders generic, wildcard and array types`() {
        val imports = ImportCollector("x")
        val type = TypeRef.iface(
            "java.util.Map",
            TypeRef.STRING,
            TypeRef.iface("java.util.List", TypeRef("?", kind = TypeKind.WILDCARD, wildcardBound = TypeRef("java.lang.Number"))),
        )
        assertEquals("Map<String, List<? extends Number>>", imports.typeText(type))
        assertEquals("int[][]", imports.typeText(TypeRef.primitive("int").copy(arrayDimensions = 2)))
    }
}
