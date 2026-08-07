plugins { id("com.github.johnrengelman.shadow") }

architectury { neoForge() }

val common: Configuration by configurations.creating
val shadowBundle: Configuration by configurations.creating
configurations {
    compileClasspath.get().extendsFrom(common)
    runtimeClasspath.get().extendsFrom(common)
}

dependencies {
    "neoForge"("net.neoforged:neoforge:${mod.dep("neoforge")}")
    common(project(path = ":${stonecutter.current.version}", configuration = "namedElements")) { isTransitive = false }
    shadowBundle(project(path = ":${stonecutter.current.version}", configuration = "transformProductionNeoForge"))
}

tasks.shadowJar { configurations = listOf(shadowBundle); archiveClassifier = "dev-shadow" }
tasks.remapJar { inputFile.set(tasks.shadowJar.get().archiveFile); archiveClassifier = null }
