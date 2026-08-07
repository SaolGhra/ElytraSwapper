import net.fabricmc.loom.api.LoomGradleExtensionAPI

// Central Stonecutter script: evaluated once per version node (and once per loader branch node).
// `stonecutter.current.version` is the Minecraft version this evaluation is for.

// Declared here rather than applied imperatively so Kotlin's type-safe accessors (`architectury {}`,
// `java {}`) are generated. Only the loom plugin has to be imperative, because its ID varies per
// version and a `plugins {}` block cannot branch.
plugins {
    java
    id("architectury-plugin")
}

val minecraft: String = stonecutter.current.version
val unobfuscated: Boolean = mod.unobfuscated
val javaVersion: Int = mod.prop("java").toInt()

// 26.1+ ships unobfuscated Minecraft: no Mojang mappings, no intermediary, nothing to remap. That
// needs a different loom plugin ID, not just a different mappings dependency — see
// .claude/docs/loader-support-matrix.md. Applied imperatively because a `plugins {}` block cannot
// branch. Both IDs are declared (apply false) in stonecutter.gradle.kts at one shared version.
apply(plugin = if (unobfuscated) "dev.architectury.loom-no-remap" else "dev.architectury.loom-remap")

version = "${mod.version}+$minecraft"
group = mod.group

base {
    archivesName.set("${mod.id}-common")
}

// Tell Architectury which loader platforms include this version, so the common source is compiled
// for exactly the branches that actually exist here (NeoForge is absent below 1.20.2).
architectury {
    common(stonecutter.tree.branches.mapNotNull {
        if (stonecutter.current.project !in it) null else it.project.prop("loom.platform")
    })
}

// Resolved here, not inside `dependencies {}` — in that block `the<T>()` resolves against the
// DependencyHandler receiver, where the loom extension is not registered.
val loomExt = extensions.getByType(LoomGradleExtensionAPI::class.java)

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/")
    maven("https://maven.architectury.dev")
    maven("https://maven.neoforged.net/releases/")
    maven("https://maven.minecraftforge.net")
}

// loom-no-remap creates no mod* dependency configurations — with nothing to remap, a mod is just a
// normal dependency. The obfuscated nodes need modImplementation so loom can remap them. This is the
// same rule the previous single-version build.gradle used for fabric-loom.
val modDep = if (unobfuscated) "implementation" else "modImplementation"

dependencies {
    "minecraft"("com.mojang:minecraft:$minecraft")

    // Official Mojang mappings for every obfuscated version. Mojang publishes these for all
    // releases, which avoids Yarn's lag and — more importantly here — keeps class names identical
    // to the unobfuscated 26.x names, so one source tree compiles against both eras. Yarn would
    // rename half the API surface and force a parallel set of Stonecutter guards for naming alone.
    if (!unobfuscated) {
        "mappings"(loomExt.officialMojangMappings())
    }

    modDep("net.fabricmc:fabric-loader:${mod.dep("fabric_loader")}")

    "io.github.llamalad7:mixinextras-common:${mod.dep("mixin_extras")}".let {
        "annotationProcessor"(it)
        "implementation"(it)
    }

    // Pure-logic unit tests for the slot-search/selection code; no Minecraft runtime needed.
    "testImplementation"("org.junit.jupiter:junit-jupiter:5.11.3")
    "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

java {
    withSourcesJar()
    val v = JavaVersion.toVersion(javaVersion)
    sourceCompatibility = v
    targetCompatibility = v
    toolchain.languageVersion.set(JavaLanguageVersion.of(javaVersion))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(javaVersion)
}

tasks.named("build") {
    group = "versioned"
    description = "Run through 'chiseledBuild' — a direct build of a non-active node produces an empty jar."
}
