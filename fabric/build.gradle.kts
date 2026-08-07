import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import net.fabricmc.loom.api.LoomGradleExtensionAPI
import net.fabricmc.loom.task.RemapJarTask

// architectury-plugin is already applied to this project by the central script, but Kotlin DSL
// accessors are generated per script file — without declaring it here, `architectury {}` will not
// resolve in this file.
plugins {
    java
    id("architectury-plugin")
    id("com.github.johnrengelman.shadow")
}

// Loader branch nodes do not auto-load versions/<mc>/gradle.properties (see versionProps), so read
// it explicitly. Unobfuscated nodes have no mod* configurations and no remapJar task.
val vprops = versionProps(stonecutter.current.version)
val unobf = vprops.getValue("mod.unobfuscated").toBoolean()
val modDep = if (unobf) "implementation" else "modImplementation"

// The central Stonecutter script applies loom in its BODY, which is evaluated after this script's
// body for a loader branch node — so the loom extension is not present yet, and architectury's
// fabric() needs it. Apply it here directly; a second apply of the same plugin is a no-op.
apply(plugin = if (unobf) "dev.architectury.loom-no-remap" else "dev.architectury.loom-remap")

architectury { fabric() }

val common: Configuration by configurations.creating
val shadowBundle: Configuration by configurations.creating
// Named lookups rather than accessors: these configurations are contributed by loom, which is
// applied imperatively above, so no type-safe accessors are generated for them.
configurations.named("compileClasspath") { extendsFrom(common) }
configurations.named("runtimeClasspath") { extendsFrom(common) }


// Loader branch nodes are standalone projects — the central Stonecutter script is NOT applied to
// them, so each declares its own Minecraft and mappings. Unobfuscated nodes take no mappings at all.
val loomExt = extensions.getByType(LoomGradleExtensionAPI::class.java)

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/")
    maven("https://maven.architectury.dev")
    maven("https://maven.neoforged.net/releases/")
}

dependencies {
    "minecraft"("com.mojang:minecraft:${stonecutter.current.version}")
    if (!unobf) "mappings"(loomExt.officialMojangMappings())
    modDep("net.fabricmc:fabric-loader:${mod.dep("fabric_loader")}")
    modDep("net.fabricmc.fabric-api:fabric-api:${vprops.getValue("deps.fabric_api")}")
    // namedElements is loom's mapped-jar configuration and does not exist on unobfuscated nodes —
    // there is no separate named jar when nothing is remapped. Fall back to the default variant.
    if (unobf) {
        common(project(":${stonecutter.current.version}")) { isTransitive = false }
    } else {
        common(project(path = ":${stonecutter.current.version}", configuration = "namedElements")) { isTransitive = false }
    }
    shadowBundle(project(path = ":${stonecutter.current.version}", configuration = "transformProductionFabric"))
}

tasks.named<ShadowJar>("shadowJar") { configurations = listOf(shadowBundle) }

// On obfuscated nodes remapJar produces the shippable jar from the shadow output. On unobfuscated
// nodes loom creates no remapJar at all, so shadowJar IS the shippable jar.
if (tasks.findByName("remapJar") != null) {
    tasks.named<ShadowJar>("shadowJar") { archiveClassifier.set("dev-shadow") }
    tasks.named<RemapJarTask>("remapJar") {
        inputFile.set(tasks.named<ShadowJar>("shadowJar").get().archiveFile)
        archiveClassifier.set("")
    }
} else {
    tasks.named<ShadowJar>("shadowJar") { archiveClassifier.set("") }
}

version = "${mod.version}+${stonecutter.current.version}"
group = mod.group
base { archivesName.set("${mod.name}-fabric-${stonecutter.current.version}") }

// fabric.mod.json carries the Minecraft range and the Java floor, both of which vary per node, so
// it is templated rather than duplicated 23 times.
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
