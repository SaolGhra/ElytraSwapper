import net.fabricmc.loom.api.LoomGradleExtensionAPI

// Kotlin DSL accessors are generated per script FILE, so the plugins this script uses must be
// declared here even though the tree already applies them to this project.
plugins {
    java
    id("architectury-plugin")
}

// Loader branch nodes do not auto-load versions/<mc>/gradle.properties — Gradle only reads a
// project's own projectDir, and a branch node's is <loader>/versions/<mc>/, which does not exist.
val vprops = versionProps(stonecutter.current.version)
val unobf = vprops.getValue("mod.unobfuscated").toBoolean()
val modDep = if (unobf) "implementation" else "modImplementation"

// The central Stonecutter script is NOT applied to loader branch nodes, so this project applies loom
// itself and declares its own Minecraft and mappings. 26.1+ is unobfuscated and takes no mappings.
apply(plugin = if (unobf) "dev.architectury.loom-no-remap" else "dev.architectury.loom-remap")

architectury { fabric() }

val loomExt = extensions.getByType(LoomGradleExtensionAPI::class.java)

// Compile the shared sources into this jar directly rather than consuming the common project as a
// jar/configuration. Every cross-project route was a dead end under Gradle 9 + architectury 3.5:
// name-based variant selection of transformProduction<Loader> is rejected outright, and both the
// Shadow route and folding the common jar in produced a compileJava <- jar cycle inside this
// project. Compiling the sources twice costs nothing at this size, needs no architectury transform
// (the common code uses no @ExpectPlatform), and leaves loom's jar -> remapJar chain untouched.
sourceSets.named("main") {
    java.srcDir(rootProject.file("src/main/java"))
    resources.srcDir(rootProject.file("src/main/resources"))
}

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/")
    maven("https://maven.architectury.dev")
}

dependencies {
    "minecraft"("com.mojang:minecraft:${stonecutter.current.version}")
    if (!unobf) "mappings"(loomExt.officialMojangMappings())

    modDep("net.fabricmc:fabric-loader:${mod.dep("fabric_loader")}")
    modDep("net.fabricmc.fabric-api:fabric-api:${vprops.getValue("deps.fabric_api")}")

}

version = "${mod.version}+${stonecutter.current.version}"
group = mod.group
base { archivesName.set("${mod.name}-fabric-${stonecutter.current.version}") }

// fabric.mod.json carries the Minecraft range and the Java floor, both of which vary per node.
tasks.processResources {
    properties(
        listOf("fabric.mod.json"),
        "id" to mod.id,
        "name" to mod.name,
        "version" to mod.version,
        "fabric_loader" to mod.dep("fabric_loader"),
        "mc_dep" to vprops.getValue("mod.mc_dep_fabric"),
        "java" to vprops.getValue("mod.java"),
    )
}

java {
    val v = JavaVersion.toVersion(vprops.getValue("mod.java"))
    sourceCompatibility = v
    targetCompatibility = v
    toolchain.languageVersion.set(JavaLanguageVersion.of(vprops.getValue("mod.java")))
}
