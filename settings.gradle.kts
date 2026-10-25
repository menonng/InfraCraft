pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("net.fabricmc.fabric-loom") version "1.19-SNAPSHOT"
        id("net.fabricmc.fabric-loom-remap") version "1.19-SNAPSHOT"
        id("org.jetbrains.kotlin.jvm") version "2.4.21"
    }
}

rootProject.name = "infracraft"

// 각 마인크래프트 버전은 versions/<버전> 아래의 독립 서브프로젝트이고,
// common/ 의 Kotlin 코드와 리소스를 함께 컴파일한다.
for (mc in listOf("26.2", "26.3")) {
    include(":mc$mc")
    project(":mc$mc").projectDir = file("versions/$mc")
}
