// 루트 프로젝트는 비어 있다. 실제 빌드는 :mc1.21.1, :mc1.21.11, :mc26.3 에서 이루어진다.
tasks.register("buildAll") {
    group = "build"
    description = "Builds InfraCraft for every supported Minecraft version."
    dependsOn(subprojects.map { "${it.path}:build" })
}
