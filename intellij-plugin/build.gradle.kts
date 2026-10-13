import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    kotlin("jvm")
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        // IDE 에 번들된 Kotlin stdlib 과 호환되도록 API 수준을 낮춰 둔다.
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_1_9)
    }
}

dependencies {
    implementation(project(":generator-core"))

    intellijPlatform {
        // 오프라인 환경: -PplatformLocalPath=/path/to/IntelliJ IDEA 로 설치된 IDE 를 SDK 로 사용할 수 있다.
        val localPlatform = providers.gradleProperty("platformLocalPath").orNull
        if (localPlatform != null) local(localPlatform) else intellijIdeaCommunity("2024.3.6")
        bundledPlugin("com.intellij.java")
        testFramework(TestFrameworkType.Platform)
        testFramework(TestFrameworkType.Plugin.Java)
    }

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.opentest4j:opentest4j:1.3.0")
}

intellijPlatform {
    pluginConfiguration {
        id = "io.github.vkeiwon.junit-test-generator"
        name = "JUnit Test Generator"
        version = project.version.toString()
        ideaVersion {
            sinceBuild = "243"
            untilBuild = provider { null }
        }
    }
}
