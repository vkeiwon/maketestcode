package io.github.vkeiwon.junitgen.plugin

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import io.github.vkeiwon.junitgen.core.JUnitTestGenerator
import io.github.vkeiwon.junitgen.core.model.TypeKind

class PsiClassModelBuilderTest : LightJavaCodeInsightFixtureTestCase() {

    fun testBuildsModelWithDependencyCalls() {
        myFixture.addClass("package com.example; public interface Repo { String find(long id); void save(String value); void save(Integer value); }")
        val service = myFixture.addClass(
            """
            package com.example;

            public class Service {
                private final Repo repo;
                private final String name = "x";

                public Service(Repo repo) { this.repo = repo; }

                public String get(long id) { return load(id); }

                private String load(long id) { return repo.find(id); }

                public void store(String value) { this.repo.save(value); }

                public String getName() { return name; }
            }
            """.trimIndent(),
        )

        val model = PsiClassModelBuilder.build(service)

        assertEquals("com.example", model.packageName)
        assertEquals(listOf("get", "store", "getName"), model.methods.map { it.name })
        assertTrue(model.methods.single { it.name == "getName" }.isAccessor)
        assertEquals(TypeKind.INTERFACE, model.fields.single { it.name == "repo" }.type.kind)
        assertTrue(model.fields.single { it.name == "name" }.hasInitializer)

        val get = model.methods.single { it.name == "get" }
        assertEquals(listOf("repo.find"), get.dependencyCalls.map { "${it.fieldName}.${it.methodName}" })
        val store = model.methods.single { it.name == "store" }
        assertTrue(store.dependencyCalls.single().isOverloaded)

        val source = JUnitTestGenerator().generate(model).source
        assertTrue(source, "service = new Service(repo);" in source)
        assertTrue(source, "when(repo.find(anyLong())).thenReturn(\"find\");" in source)
        assertTrue(source, "verify(repo).save(any(String.class));" in source)
    }

    fun testInterfacesAreNotSupported() {
        val repo = myFixture.addClass("package com.example; public interface Repo { }")
        assertNotNull(PsiClassModelBuilder.unsupportedReason(repo))
    }
}
