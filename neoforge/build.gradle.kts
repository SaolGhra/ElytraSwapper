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
// neoForge() needs it. Apply it here directly; a second apply of the same plugin is a no-op.
apply(plugin = if (unobf) "dev.architectury.loom-no-remap" else "dev.architectury.loom-remap")

architectury { neoForge() }

val common: Configuration by configurations.creating
val shadowBundle: Configuration by configurations.creating
// Named lookups rather than accessors: these configurations are contributed by loom, which is
// applied imperatively above, so no type-safe accessors are generated for them.
configurations.named("compileClasspath") { extendsFrom(common) }
configurations.named("runtimeClasspath") { extendsFrom(common) }


// Loader branch nodes are standalone projects — the central Stonecutter script is NOT applied to
// them, so each declares its own Minecraft and mappings. Unobfuscated nodes take no mappings at all.
val loomExt = extensions.getByType(LoomGradleExtensionAPI::class.java)

// Hoisted: inside `dependencies {}` the name `project` is DependencyHandler's dependency factory,
// not Project.project(), so `.tasks` is not reachable from there.
val commonProject = project(":${stonecutter.current.version}")

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
    // namedElements is loom's mapped-jar configuration and does not exist on unobfuscated nodes —
    // there is no separate named jar when nothing is remapped. Fall back to the default variant.
    if (unobf) {
        common(project(":${stonecutter.current.version}")) { isTransitive = false }
    } else {
        common(project(path = ":${stonecutter.current.version}", configuration = "namedElements")) { isTransitive = false }
    }
    // Bundle the common jar directly instead of architectury's transformProductionNeoForge output.
    // Two reasons: Gradle 9 refuses name-based variant selection, so the documented
    // project(path, configuration = "transformProductionNeoForge") idiom fails outright; and the
    // transform task has a back-edge to this project, so consuming its output as a task dependency
    // is a build cycle. The transformer is a no-op here regardless — the common module uses no
    // @ExpectPlatform and no architectury runtime API, so there is nothing for it to rewrite.
    shadowBundle(files(commonProject.tasks.named("jar")))
}

tasks.named<ShadowJar>("shadowJar") { configurations = listOf(shadowBundle) }

// On obfuscated nodes remapJar produces the shippable jar from the shadow output. On unobfuscated
// nodes loom creates no remapJar at all, so shadowJar IS the shippable jar.
// tasks.names does not realise tasks; findByName does, and realising loom's remapJar
// mid-configuration is enough to create a build cycle.
if (tasks.names.contains("remapJar")) {
    tasks.named<ShadowJar>("shadowJar") { archiveClassifier.set("dev-shadow") }
    tasks.named<RemapJarTask>("remapJar") {
        // flatMap, not .get(): eagerly realising shadowJar mid-configuration is what produced a
        // compileJava <- jar build cycle here.
        inputFile.set(tasks.named<ShadowJar>("shadowJar").flatMap { it.archiveFile })
        archiveClassifier.set("")
    }
} else {
    tasks.named<ShadowJar>("shadowJar") { archiveClassifier.set("") }
}
