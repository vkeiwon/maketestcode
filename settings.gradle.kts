rootProject.name = "junit-test-generator"

include("generator-core")

// IntelliJ Platform SDK를 받을 수 없는 환경(CI 등)에서는 -PskipIntellijPlugin=true 로 core만 빌드할 수 있다.
if (providers.gradleProperty("skipIntellijPlugin").orNull != "true") {
    include("intellij-plugin")
}
