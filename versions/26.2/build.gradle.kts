import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("net.fabricmc.fabric-loom")
    id("org.jetbrains.kotlin.jvm")
}

val minecraftVersion = property("minecraft_version") as String
val flkVersion = property("flk_version") as String
val javaVersion = (property("java_version") as String).toInt()

version = "${property("mod_version")}+mc$minecraftVersion"
group = property("maven_group") as String
base.archivesName.set(property("archives_base_name") as String)

sourceSets.main {
    resources.srcDir(rootProject.file("common/resources"))
}
kotlin.sourceSets.named("main") {
    kotlin.srcDir(rootProject.file("common/kotlin"))
}

// 클라이언트 게임테스트: 실제 게임 창(CI 에서는 Xvfb)을 띄워 월드를 만들고 다리·홀로그램·아스트라 화면 스크린샷을 남긴다.
// ./gradlew :mc26.2:runClientGameTest → run/clientGameTest/screenshots/*.png
fabricApi {
    configureTests {
        createSourceSet = true
        modId = "infracraft-gametest"
        enableGameTests = false
        enableClientGameTests = true
        eula = true
    }
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")

    implementation("net.fabricmc:fabric-loader:${property("loader_version")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")
    implementation("net.fabricmc:fabric-language-kotlin:${property("flk_version")}")
}

tasks.processResources {
    // 버전별 리소스(src/main/resources)가 공통 리소스보다 먼저 오므로, 겹치면 버전별 파일이 이긴다.
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    val props = mapOf("version" to project.version.toString(), "flk_version" to flkVersion)
    inputs.properties(props)
    filesMatching("fabric.mod.json") { expand(props) }
}

java {
    sourceCompatibility = JavaVersion.toVersion(javaVersion)
    targetCompatibility = JavaVersion.toVersion(javaVersion)
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(javaVersion)
    options.encoding = "UTF-8"
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.fromTarget(javaVersion.toString()))
        freeCompilerArgs.add("-Xjdk-release=$javaVersion")
    }
}

// CI 스모크 테스트: 전용 서버를 띄워 등록·믹스인·데이터팩 로딩을 확인한 뒤 바로 종료한다.
tasks.register("smokeServer") {
    group = "verification"
    dependsOn("runServer")
}
val smokeRunDir = layout.projectDirectory.dir("run").asFile
tasks.matching { it.name == "runServer" }.configureEach {
    (this as JavaExec).apply {
        jvmArgs("-Dinfracraft.smoke=true")
        doFirst {
            val runDir = smokeRunDir
            runDir.mkdirs()
            runDir.resolve("eula.txt").writeText("eula=true\n")
            runDir.resolve("server.properties").writeText("online-mode=false\nlevel-type=minecraft\\:flat\nspawn-protection=0\n")
        }
    }
}
