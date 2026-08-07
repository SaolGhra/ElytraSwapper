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

architectury { neoForge() }

val loomExt = extensions.getByType(LoomGradleExtensionAPI::class.java)

// Share the generated sources rather than consuming the common project as a jar or configuration.
// Every cross-project route failed under Gradle 9 + architectury 3.5: name-based selection of
// transformProduction<Loader> is rejected outright, and consuming the common jar — as a dependency,
// a task output, or through Shadow — produces a build cycle inside this project.
//
// The catch is that Stonecutter keeps ONE physical copy of src/ and rewrites it as the active
// version changes, so this is only safe when a single version is active for the whole Gradle
// invocation. Build the matrix one version per invocation (see buildMatrix.sh), NOT with
// chiseledBuild, which switches versions mid-invocation and races this.
sourceSets.named("main") {
    java.srcDir(rootProject.file("src/main/java"))
    resources.srcDir(rootProject.file("src/main/resources"))
}

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/")
    maven("https://maven.architectury.dev")
    maven("https://maven.neoforged.net/releases/")
}

dependencies {

    "minecraft"("com.mojang:minecraft:${stonecutter.current.version}")
    if (!unobf) "mappings"(loomExt.officialMojangMappings())
    "neoForge"("net.neoforged:neoforge:${vprops.getValue("deps.neoforge")}")
}

version = "${mod.version}+${stonecutter.current.version}"
group = mod.group
base { archivesName.set("${mod.name}-neoforge-${stonecutter.current.version}") }

// neoforge.mods.toml carries the Minecraft range and the loader version, both of which vary per node.
tasks.processResources {
    properties(
        listOf("META-INF/neoforge.mods.toml"),
        "id" to mod.id,
        "name" to mod.name,
        "version" to mod.version,
        "mc_dep" to vprops.getValue("mod.mc_dep_forgelike"),
        "neoforge" to vprops.getValue("deps.neoforge"),
    )
}

java {
    val v = JavaVersion.toVersion(vprops.getValue("mod.java"))
    sourceCompatibility = v
    targetCompatibility = v
    toolchain.languageVersion.set(JavaLanguageVersion.of(vprops.getValue("mod.java")))
}

// Collects this node's shippable jar into a single tree at build/libs/<mod version>/neoforge/, which
// is what the chiseled matrix tasks and CI archive from. Excludes the dev and sources jars.
tasks.register<Copy>("buildAndCollect") {
    group = "versioned"
    dependsOn(tasks.named("build"))
    from(layout.buildDirectory.dir("libs")) {
        include("*.jar")
        exclude("*-dev.jar", "*-dev-shadow.jar", "*-sources.jar")
    }
    into(rootProject.layout.buildDirectory.dir("libs/${mod.version}/neoforge"))
}
