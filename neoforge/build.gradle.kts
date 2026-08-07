import net.fabricmc.loom.api.LoomGradleExtensionAPI

// Kotlin DSL accessors are generated per script FILE, so the plugins this script uses must be
// declared here even though the tree already applies them to this project.
plugins {
    java
    id("architectury-plugin")
    id("me.modmuss50.mod-publish-plugin")
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

// The task that produces the shippable jar. Unobfuscated nodes have nothing to remap, so loom
// registers no remapJar there and `jar` is already the final artifact.
// org.gradle.jvm.tasks.Jar, not the bundling Jar the Kotlin DSL resolves by default: loom's
// RemapJarTask extends the former, which is the parent of the latter.
val productionJar = tasks.named<org.gradle.jvm.tasks.Jar>(if (unobf) "jar" else "remapJar")

// Collects this node's shippable jar into a single tree at build/libs/<mod version>/neoforge/,
// which is what CI archives and publishes from.
//
// Takes the archive task's output rather than globbing build/libs: that directory keeps whatever
// earlier builds left in it, and a jar from a previous configuration of this project stayed behind
// under its old name and got collected alongside the real one — a 2 KB jar holding the entrypoint
// and no common classes, which installs cleanly and does nothing.
tasks.register<Copy>("buildAndCollect") {
    group = "versioned"
    dependsOn(tasks.named("build"))
    from(productionJar)
    into(rootProject.layout.buildDirectory.dir("libs/${mod.version}/neoforge"))
}

// One Modrinth version per Minecraft version rather than one file listing every game version: a
// single jar cannot be correct for all of them, and a player picking "1.20.4" should get the jar
// built against 1.20.4 rather than a range that happens to include it.
publishMods {
    file.set(productionJar.flatMap { it.archiveFile })
    displayName.set("${mod.name} ${mod.version} — NeoForge ${stonecutter.current.version}")
    // Modrinth requires this to be unique across the whole project, so it carries the loader too.
    version.set("${mod.version}+${stonecutter.current.version}-neoforge")
    changelog.set(providers.gradleProperty("changelog").orElse("ElytraSwapper ${mod.version}"))
    type.set(me.modmuss50.mpp.ReleaseType.STABLE)

    // -PdryRunPublish exercises the whole publish path — metadata, file selection, the lot —
    // and writes the payload to disk instead of uploading it. The alternative way to find out
    // whether 44 uploads are configured correctly is to do 44 uploads.
    dryRun.set(providers.gradleProperty("dryRunPublish").map { true }.orElse(false))

    modLoaders.add("neoforge")

    modrinth {
        projectId.set("vITmEkpU")
        accessToken.set(providers.environmentVariable("MODRINTH_TOKEN"))
        minecraftVersions.add(stonecutter.current.version)
    }
}
