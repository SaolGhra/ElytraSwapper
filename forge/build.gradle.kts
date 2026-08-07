plugins { id("com.github.johnrengelman.shadow") }

architectury { forge() }

val common: Configuration by configurations.creating
val shadowBundle: Configuration by configurations.creating
configurations {
    compileClasspath.get().extendsFrom(common)
    runtimeClasspath.get().extendsFrom(common)
}

dependencies {
    "forge"("net.minecraftforge:forge:${stonecutter.current.version}-${mod.dep("forge")}")
    common(project(path = ":${stonecutter.current.version}", configuration = "namedElements")) { isTransitive = false }
    shadowBundle(project(path = ":${stonecutter.current.version}", configuration = "transformProductionForge"))
}

tasks.shadowJar { configurations = listOf(shadowBundle); archiveClassifier = "dev-shadow" }
tasks.remapJar { inputFile.set(tasks.shadowJar.get().archiveFile); archiveClassifier = null }
