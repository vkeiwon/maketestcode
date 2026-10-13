package io.github.vkeiwon.junitgen.core

import io.github.vkeiwon.junitgen.core.model.ClassModel
import io.github.vkeiwon.junitgen.core.model.MethodModel
import io.github.vkeiwon.junitgen.core.model.ParameterModel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JUnitTestGeneratorTest {

    private val generator = JUnitTestGenerator()

    @Test
    fun `constructor injection uses mocks as constructor arguments`() {
        val source = generator.generate(SampleModels.userService).source

        assertTrue("@ExtendWith(MockitoExtension.class)" in source, source)
        assertTrue("@Mock\n    private UserRepository userRepository;" in source, source)
        assertTrue("userService = new UserService(userRepository, notifier, \"prefix\");" in source, source)
        assertFalse("@InjectMocks" in source, source)
        assertFalse("private String prefix" in source, "String 필드는 Mock 대상이 아니다")
    }

    @Test
    fun `stubs and verifies dependency calls`() {
        val source = generator.generate(SampleModels.userService).source

        assertTrue("when(userRepository.findById(any())).thenReturn(Optional.of(new User()));" in source, source)
        assertTrue("when(userRepository.save(any())).thenReturn(new User());" in source, source)
        assertTrue("verify(notifier).send(any(User.class));" in source, "오버로드된 메서드는 타입을 명시해야 한다\n$source")
        assertTrue("verify(userRepository).deleteById(any());" in source, source)
        assertTrue("when(userRepository.findAll()).thenReturn(List.of(new User()));" in source, source)
    }

    @Test
    fun `field injection falls back to InjectMocks`() {
        val source = generator.generate(SampleModels.legacyUserService).source

        assertTrue("@InjectMocks\n    private LegacyUserService legacyUserService;" in source, source)
        assertFalse("setUp" in source, source)
    }

    @Test
    fun `test names are unique for overloads and exceptions`() {
        val test = generator.generate(SampleModels.calculator)

        assertEquals(
            listOf(
                "add_int_int_success", "add_double_double_success", "divide_success", "divide_throwsArithmeticException",
                "scale_success", "reset_success", "isPositive_success",
            ),
            test.testMethodNames,
        )
        assertTrue("@Disabled(" in test.source)
        assertTrue("void divide_success() throws Exception {" in test.source, test.source)
    }

    @Test
    fun `static only class has no instance field`() {
        val model = ClassModel(
            "util", "StringUtils", hasOnlyPrivateConstructors = true,
            methods = listOf(MethodModel("isBlank", SampleModels.BOOLEAN, listOf(ParameterModel("text", SampleModels.STRING)), isStatic = true)),
        )
        val source = generator.generate(model).source

        assertFalse("private StringUtils" in source, source)
        assertTrue("boolean result = StringUtils.isBlank(text);" in source, source)
    }

    @Test
    fun `local variables do not shadow fields`() {
        val model = SampleModels.legacyUserService.copy(
            methods = listOf(MethodModel("copy", SampleModels.USER, listOf(ParameterModel("userRepository", SampleModels.STRING), ParameterModel("result", SampleModels.INT)))),
        )
        val source = generator.generate(model).source

        assertTrue("String userRepository2 = \"userRepository\";" in source, source)
        assertTrue("User actual = legacyUserService.copy(userRepository2, result);" in source, source)
    }

    @Test
    fun `junit4 output uses public members and runner`() {
        val source = JUnitTestGenerator(GeneratorOptions(junitVersion = JUnitVersion.JUNIT4)).generate(SampleModels.userService).source

        assertTrue("@RunWith(MockitoJUnitRunner.class)\npublic class UserServiceTest {" in source, source)
        assertTrue("@Before\n    public void setUp() {" in source, source)
        assertTrue("import static org.junit.Assert.assertNotNull;" in source, source)
        assertFalse("DisplayName" in source, source)
    }

    @Test
    fun `decapitalize handles acronyms`() {
        assertEquals("userService", JUnitTestGenerator.decapitalize("UserService"))
        assertEquals("urlParser", JUnitTestGenerator.decapitalize("URLParser"))
        assertEquals("url", JUnitTestGenerator.decapitalize("URL"))
        assertEquals("a", JUnitTestGenerator.decapitalize("A"))
    }
}
