package io.github.vkeiwon.junitgen.core

import io.github.vkeiwon.junitgen.core.model.FieldModel
import io.github.vkeiwon.junitgen.core.model.TypeKind
import io.github.vkeiwon.junitgen.core.model.TypeRef
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DependencyDetectorTest {

    @Test
    fun `detects collaborators but not values`() {
        val fields = listOf(
            FieldModel("repository", TypeRef.iface("x.Repository")),
            FieldModel("client", TypeRef("x.HttpClient")),
            FieldModel("clock", TypeRef("java.time.Clock", kind = TypeKind.ABSTRACT_CLASS)),
            FieldModel("name", TypeRef.STRING),
            FieldModel("count", TypeRef.primitive("int")),
            FieldModel("cache", TypeRef.iface("java.util.Map")),
            FieldModel("builder", TypeRef("java.lang.StringBuilder")),
            FieldModel("mapper", TypeRef("x.Mapper"), hasInitializer = true),
            FieldModel("injected", TypeRef("x.Mapper"), hasInitializer = true, isInjected = true),
            FieldModel("LOG", TypeRef.iface("org.slf4j.Logger"), isStatic = true),
        )
        assertEquals(listOf("repository", "client", "clock", "injected"), DependencyDetector.detect(fields).map { it.name })
    }
}
