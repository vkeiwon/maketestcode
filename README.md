# JUnit Test Generator (IntelliJ 플러그인)

Java 클래스를 분석해 **규칙/템플릿 기반으로 JUnit 테스트 코드를 자동 생성**하는 IntelliJ IDEA 플러그인입니다.
LLM이나 네트워크를 전혀 사용하지 않고, IDE의 PSI(코드 구조 분석) 정보만으로 결정적으로 동작합니다.

## 무엇을 생성하나요?

| 대상 | 생성 규칙 |
| --- | --- |
| private 이 아닌 메서드 | `메서드명_success` 테스트 (given / when / then) |
| 메서드 파라미터 | 타입별 기본값으로 지역 변수 생성 (`int → 1`, `String → "파라미터명"`, `List<T> → List.of(T값)`, `Optional<T> → Optional.of(T값)`, enum → 첫 상수, POJO → `new X()` …) |
| 협력 객체 필드 (인터페이스, 서비스/리포지토리 등) | `@Mock` 필드 |
| 테스트 대상 생성 | 생성자 주입이면 `@BeforeEach`에서 `new Service(mock1, mock2, "값")`, 필드 주입(`@Autowired` 등)이면 `@InjectMocks` |
| 메서드가 호출하는 의존성 메서드 (값 반환) | `when(repo.findById(any())).thenReturn(Optional.of(new User()));` |
| 메서드가 호출하는 의존성 메서드 (void) | `verify(notifier).send(any(User.class));` |
| 반환값 | 참조 타입 → `assertNotNull(result)`, 기본 타입 → `// TODO: assertEquals(expected, result);` |
| void 메서드 (검증할 호출 없음) | `assertDoesNotThrow(() -> ...)` |
| `throws` 절의 예외 | `메서드명_throwsXxxException` 테스트 골격 (`@Disabled` 상태로 생성 → 조건을 채운 뒤 활성화) |
| static 메서드 | `ClassName.method(...)` 로 호출, 인스턴스 생성 생략 |
| 추상 클래스 | `mock(Foo.class, CALLS_REAL_METHODS)` |
| 오버로드 메서드 | `add_int_int_success`, `add_double_double_success` 처럼 파라미터 타입으로 이름 구분 |

의존성 호출 분석은 메서드 본문뿐 아니라 **같은 클래스의 헬퍼 메서드(private 포함)까지 따라가며** 수집합니다.
이미 테스트 클래스가 있으면 덮어쓰지 않고 **없는 테스트 메서드·필드·import만 추가**합니다.

### 생성 예시

```java
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private Notifier notifier;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, notifier, "prefix");
    }

    @Test
    @DisplayName("register(String, Grade) - 정상 동작")
    void register_success() {
        // given
        String name = "name";
        Grade grade = Grade.BRONZE;
        when(userRepository.save(any())).thenReturn(new User());

        // when
        User result = userService.register(name, grade);

        // then
        assertNotNull(result);
        verify(notifier).send(any(User.class));
    }

    @Test
    @Disabled("TODO: 예외가 발생하는 조건을 설정한 뒤 활성화하세요")
    @DisplayName("getUser(Long) - UserNotFoundException 발생")
    void getUser_throwsUserNotFoundException() {
        // given
        Long id = 1L;
        // TODO: UserNotFoundException 이(가) 발생하도록 입력값 또는 Mock 동작을 설정하세요

        // when & then
        assertThrows(UserNotFoundException.class, () -> userService.getUser(id));
    }
}
```

## 사용법

- **에디터**: 클래스 안에서 `Alt+Insert`(macOS `⌘N`) → **JUnit 테스트 생성**
- **프로젝트 뷰**: Java 파일이나 패키지/디렉터리를 (여러 개) 선택 → 우클릭 → **JUnit 테스트 생성** → 하위 클래스 전체 일괄 생성

테스트 파일은 같은 모듈의 테스트 소스 루트(`src/test/java`)의 같은 패키지에 생성됩니다.
Gradle의 source set별 모듈 구조도 지원하며, 테스트 루트가 없으면 `src/main/java` 옆에 `src/test/java`를 만듭니다.
생성 후 테스트 클래스패스에 JUnit/Mockito/AssertJ가 없으면 추가해야 할 의존성을 알려줍니다.

### 설정 (Settings → Tools → JUnit Test Generator)

- JUnit 버전: JUnit 5 (Jupiter) / JUnit 4
- Assertion 스타일: JUnit Assertions / AssertJ
- Mockito 사용 여부
- `@DisplayName` 생성 여부 (JUnit 5)
- `throws` 예외 테스트 골격 생성 여부
- 단순 getter/setter 제외 여부
- 테스트 클래스 접미사 (기본 `Test`)

## 프로젝트 구조

```
generator-core/    IDE와 무관한 순수 Kotlin 생성 엔진 (모델 → Java 테스트 소스)
  model/           TypeRef, ClassModel, MethodModel, DependencyCall ...
  JUnitTestGenerator.kt   테스트 소스 렌더링 (규칙의 핵심)
  ValueFactory.kt         타입별 기본값 / Mockito 매처
  DependencyDetector.kt   Mock 대상 필드 판별
  ImportCollector.kt      import 관리 및 이름 충돌 처리
intellij-plugin/   IntelliJ 연동
  PsiClassModelBuilder.kt PSI → ClassModel (의존성 호출 분석 포함)
  PsiTypeConverter.kt     PsiType → TypeRef
  TestFileWriter.kt       테스트 루트 탐색, 파일 생성/병합
  GenerateJUnitTestAction.kt  액션 (Generate 메뉴, 프로젝트 뷰)
  settings/               설정 저장 및 설정 화면
```

생성 규칙은 모두 `generator-core`에 있어 IDE 없이 단위 테스트할 수 있습니다.
`generator-core`의 테스트는 샘플 Java 소스(`src/test/resources/samples`)에 대해 **생성 → javac 컴파일 → JUnit 실행**을
JUnit 4/5 × JUnit/AssertJ × Mockito 사용/미사용 전 조합으로 수행해, 생성된 코드가 실제로 컴파일되고 통과하는지 검증합니다.

## 빌드

요구사항: JDK 21

```bash
# 생성 엔진만 빌드/테스트 (IntelliJ SDK 불필요)
./gradlew :generator-core:test -PskipIntellijPlugin=true

# 플러그인 빌드 → intellij-plugin/build/distributions/*.zip
./gradlew :intellij-plugin:buildPlugin

# 플러그인을 설치한 샌드박스 IDE 실행
./gradlew :intellij-plugin:runIde

# PSI 어댑터 테스트
./gradlew :intellij-plugin:test
```

설치: IntelliJ → Settings → Plugins → ⚙ → **Install Plugin from Disk...** → 빌드된 zip 선택.

### 오프라인/사내망 환경

플러그인 자체는 실행 중 네트워크를 쓰지 않습니다. 다만 **빌드할 때**는 Gradle 플러그인과 의존성, IntelliJ SDK가 필요합니다.

- IntelliJ SDK 대신 PC에 설치된 IntelliJ를 사용할 수 있습니다:
  `./gradlew :intellij-plugin:buildPlugin -PplatformLocalPath="/Applications/IntelliJ IDEA CE.app"`
- Maven 의존성은 사내 Nexus/Artifactory 미러를 `settings.gradle.kts`의 저장소나 Gradle init 스크립트로 지정하면 됩니다.
- 한 번 빌드한 zip은 네트워크 없이 어느 IntelliJ(2024.3 이상)에나 설치할 수 있습니다.

## 한계와 향후 개선 아이디어

- 기대값(assert 대상 값)은 코드만으로 알 수 없으므로 기본 타입 반환은 `TODO`로 남깁니다.
- 분기 조건(if/else)별 테스트 케이스 분리, 경계값(0, 음수, 빈 문자열, null) 파라미터화 테스트 생성은 아직 지원하지 않습니다.
- 생성 규칙(기본값, 테스트 이름 패턴)을 설정 화면에서 커스터마이징하는 기능은 추후 추가할 수 있습니다.
