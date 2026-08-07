plugins { id("com.github.johnrengelman.shadow") }

architectury { fabric() }

val common: Configuration by configurations.creating
val shadowBundle: Configuration by configurations.creating
configurations {
    compileClasspath.get().extendsFrom(common)
    runtimeClasspath.get().extendsFrom(common)
}

dependencies {
    modImplementation("net.fabricmc:fabric-loader:${mod.dep("fabric_loader")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${mod.dep("fabric_api")}")
    common(project(path = ":${stonecutter.current.version}", configuration = "namedElements")) { isTransitive = false }
    shadowBundle(project(path = ":${stonecutter.current.version}", configuration = "transformProductionFabric"))
}

tasks.shadowJar { configurations = listOf(shadowBundle); archiveClassifier = "dev-shadow" }
tasks.remapJar { inputFile.set(tasks.shadowJar.get().archiveFile); archiveClassifier = null }
