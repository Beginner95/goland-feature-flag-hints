plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.21"
    id("org.jetbrains.intellij") version "1.17.2"
}

group = "ru.webvaha"
version = "1.0.0"

repositories {
    mavenCentral()
}

intellij {
    // GoLand. Обновить версию при необходимости:
    // актуальный список: https://www.jetbrains.com/goland/download/other.html
    version.set("2023.3.2")
    type.set("GO")
    // Явно подключаем bundled Go plugin, чтобы com.goide.psi.* попал в classpath
    plugins.set(listOf("org.jetbrains.plugins.go"))
}

kotlin {
    jvmToolchain(17)
}

tasks {
    patchPluginXml {
        sinceBuild.set("233")   // GoLand 2023.3
        untilBuild.set("")      // без верхней границы — совместим с любой версией
    }
}
