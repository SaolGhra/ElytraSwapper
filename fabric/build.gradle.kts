import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import net.fabricmc.loom.task.RemapJarTask

// architectury-plugin is already applied to this project by the central script, but Kotlin DSL
// accessors are generated per script file — without declaring it here, `architectury {}` will not
// resolve in this file.
plugins {
    java
    id("architectury-plugin")
    id("com.github.johnrengelman.shadow")
}

architectury { fabric() }

val common: Configuration by configurations.creating
val shadowBundle: Configuration by configurations.creating
// Named lookups: these subprojects do not declare the `java` plugin themselves, so the type-safe
// compileClasspath/runtimeClasspath accessors are not generated here.
configurations.named("compileClasspath") { extendsFrom(common) }
configurations.named("runtimeClasspath") { extendsFrom(common) }

// See the root script: unobfuscated nodes have no mod* configurations and no remapJar task.
val unobf = mod.unobfuscated
val modDep = if (unobf) "implementation" else "modImplementation"

dependencies {
    modDep("net.fabricmc:fabric-loader:${mod.dep("fabric_loader")}")
    modDep("net.fabricmc.fabric-api:fabric-api:${mod.dep("fabric_api")}")
    common(project(path = ":${stonecutter.current.version}", configuration = "namedElements")) { isTransitive = false }
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
