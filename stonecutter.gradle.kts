plugins {
    id("dev.kikugie.stonecutter")
    // Both loom plugin IDs are declared here and applied per-node in build.gradle.kts. 26.1+ ships
    // unobfuscated Minecraft with no mappings of any kind, which needs `loom-no-remap`; everything
    // 1.21.11 and older is obfuscated and needs `loom-remap`. One plugin VERSION serves both.
    id("dev.architectury.loom-remap") version "1.17.491" apply false
    id("dev.architectury.loom-no-remap") version "1.17.491" apply false
    id("architectury-plugin") version "3.5.169" apply false
    id("com.github.johnrengelman.shadow") version "8.1.1" apply false
    id("me.modmuss50.mod-publish-plugin") version "0.8.4" apply false
}

stonecutter active "1.21.2" /* [SC] DO NOT EDIT */

// There are deliberately no chiseled build/publish tasks here.
//
// A chiseled task switches the active version repeatedly inside ONE Gradle invocation. That is fine
// when each node compiles its own sources, but the loader projects compile the shared tree at
// src/main/java, and Stonecutter rewrites that single physical copy in place as the active version
// changes. The two race: nodes end up compiling whichever era happened to be materialised at the
// time, which surfaces much later as an unrelated-looking compile error on some other version.
//
// Build and publish the matrix with ./buildMatrix.sh and ./publishMatrix.sh instead — one active
// version per Gradle invocation, which makes the race impossible rather than unlikely.

// ---------------------------------------------------------------------------------------------
// auditJars — static verification of the jars the matrix has already produced.
//
// A build that compiles is not a jar that works. Every check below corresponds to something that
// has actually gone wrong here: a jar that packaged the entrypoint and none of the common classes
// (installs fine, does nothing), metadata still holding its ${placeholders}, and sources compiled
// against the wrong Minecraft API era.
//
// Name-based era checks only work where names survive into the production jar. Fabric jars for
// 1.20 - 1.21.11 are remapped to intermediary, so Minecraft classes there become class_1234 and
// cannot be matched by name; Fabric API and NeoForge names are never remapped. Each check is
// applied only where it is meaningful, and the total number that ran is reported so a silently
// skipped rule shows up as a drop in the count.
// ---------------------------------------------------------------------------------------------

/** "1.21.10" is newer than "1.21.2" — which is exactly backwards under string comparison. */
fun mcAtLeast(version: String, floor: String): Boolean {
    val a = version.split('.').map { it.toIntOrNull() ?: 0 }
    val b = floor.split('.').map { it.toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    return true
}

val auditLibsDir = rootProject.layout.buildDirectory.dir("libs")
val auditVersionsDir = rootProject.layout.projectDirectory.dir("versions")
val auditModVersion = rootProject.property("mod.version").toString()

tasks.register("auditJars") {
    group = "verification"
    description = "Static audit of every collected jar: contents, metadata, Java target and Minecraft API era."

    doLast {
        val libs = auditLibsDir.get().asFile
        val jars = libs.walkTopDown()
            .filter { it.isFile && it.extension == "jar" && !it.name.endsWith("-sources.jar") }
            .sortedBy { it.path }
            .toList()
        require(jars.isNotEmpty()) { "No jars under $libs — run ./buildMatrix.sh first" }

        var checksRun = 0
        val failures = mutableListOf<String>()

        for (jar in jars) {
            val loader = jar.parentFile.name
            // Loader jars are versioned "<mod version>+<minecraft version>", so the Minecraft
            // version is whatever follows the last '+'. Anything else here is not one of ours —
            // which is itself worth failing on, since it would be published alongside the rest.
            val mc = jar.nameWithoutExtension.substringAfterLast('+', "")
            val propsFile = auditVersionsDir.file("$mc/gradle.properties").asFile
            if (mc.isEmpty() || !propsFile.isFile) {
                failures += "$loader/${jar.name}: not a jar this matrix builds (no versions/$mc/)"
                continue
            }

            val props = java.util.Properties().apply { propsFile.inputStream().use { load(it) } }
            val expectedJava = props.getProperty("mod.java").toInt()
            val unobfuscated = props.getProperty("mod.unobfuscated").toBoolean()

            val entries = mutableMapOf<String, ByteArray>()
            java.util.zip.ZipFile(jar).use { zip ->
                for (entry in zip.entries()) {
                    if (!entry.isDirectory) entries[entry.name] = zip.getInputStream(entry).readBytes()
                }
            }

            fun check(condition: Boolean, message: String) {
                checksRun++
                if (!condition) failures += "$loader/$mc: $message"
            }

            /** True when any class of ours names this type in its constant pool. */
            fun referenced(name: String): Boolean = entries
                .filterKeys { it.startsWith("com/saolghra/elytraswapper/") && it.endsWith(".class") }
                .values.any { String(it, Charsets.ISO_8859_1).contains(name) }

            fun requireEra(present: String, absent: String, why: String) {
                check(referenced(present), "$why — expected a reference to $present")
                check(!referenced(absent), "$why — still references $absent")
            }

            // 1. The classes that have to be present. This is the check that catches a jar built
            //    before the common sources were wired in: it packages cleanly and does nothing.
            check("com/saolghra/elytraswapper/ElytraSwapper.class" in entries, "missing ElytraSwapper.class")
            check("com/saolghra/elytraswapper/InventoryUtils.class" in entries, "missing InventoryUtils.class")
            check("com/saolghra/elytraswapper/SwapLogic.class" in entries, "missing SwapLogic.class")

            val entrypoint = when (loader) {
                "fabric" -> "com/saolghra/elytraswapper/fabric/ElytraSwapperFabric.class"
                "neoforge" -> "com/saolghra/elytraswapper/neoforge/ElytraSwapperNeoForge.class"
                else -> null
            }
            check(entrypoint != null, "unknown loader directory '$loader'")
            if (entrypoint != null) check(entrypoint in entries, "missing entrypoint $entrypoint")

            // 2. Every class targets the Java release this Minecraft version runs on. Too new and
            //    the game refuses to load it; too old means the toolchain was not the one this node
            //    asked for, which is quiet until something else breaks.
            for ((name, bytes) in entries) {
                if (!name.startsWith("com/saolghra/") || !name.endsWith(".class")) continue
                val major = ((bytes[6].toInt() and 0xFF) shl 8) or (bytes[7].toInt() and 0xFF)
                check(major == expectedJava + 44,
                        "$name is class-file major $major, expected ${expectedJava + 44} (Java $expectedJava)")
            }

            // 3. Metadata: present and fully templated. An un-expanded ${...} makes the mod
            //    unloadable with a parse error rather than a useful message.
            val metaName = if (loader == "fabric") "fabric.mod.json" else "META-INF/neoforge.mods.toml"
            val meta = entries[metaName]?.toString(Charsets.UTF_8)
            check(meta != null, "missing $metaName")
            if (meta != null) {
                check(!meta.contains("\${"), "$metaName still contains an un-expanded \${...} placeholder")
                check(meta.contains(auditModVersion), "$metaName does not mention version $auditModVersion")
                // Only Fabric names its entrypoint in metadata; NeoForge finds @Mod by scanning.
                if (loader == "fabric") {
                    check(meta.contains("com.saolghra.elytraswapper.fabric.ElytraSwapperFabric"),
                            "fabric.mod.json does not declare the entrypoint")
                    check(meta.contains("\"environment\": \"client\""),
                            "fabric.mod.json is not marked client-only")
                }
            }
            check("assets/elytraswapper/lang/en_us.json" in entries, "missing the language file")

            // 4. Loader API era. These names survive remapping, so they can be checked everywhere.
            if (loader == "fabric") {
                if (mcAtLeast(mc, "26.1")) {
                    requireEra("fabric/api/client/keymapping/v1/KeyMappingHelper",
                            "fabric/api/client/keybinding/v1/KeyBindingHelper",
                            "Fabric renamed the key mapping module at 26.1")
                } else {
                    requireEra("fabric/api/client/keybinding/v1/KeyBindingHelper",
                            "fabric/api/client/keymapping/v1/KeyMappingHelper",
                            "the key mapping module is only named that way from 26.1")
                }
            }

            if (loader == "neoforge") {
                if (mcAtLeast(mc, "1.20.5")) {
                    requireEra("net/neoforged/neoforge/client/event/ClientTickEvent",
                            "net/neoforged/neoforge/event/TickEvent",
                            "NeoForge 20.5 split the phased TickEvent into Pre/Post")
                } else {
                    requireEra("net/neoforged/neoforge/event/TickEvent",
                            "net/neoforged/neoforge/client/event/ClientTickEvent",
                            "the Pre/Post tick events only exist from NeoForge 20.5")
                }
                // Below 1.20.6 @Mod has no dist attribute, so the mod has to refuse to run on a
                // server itself. Without that guard it loads on a dedicated server and dies
                // reaching for a client class.
                val guarded = referenced("net/neoforged/fml/loading/FMLEnvironment")
                check(guarded == !mcAtLeast(mc, "1.20.6"),
                        if (mcAtLeast(mc, "1.20.6"))
                            "@Mod(dist = CLIENT) covers this version; the FMLEnvironment guard should have been compiled out"
                        else "no FMLEnvironment dist guard, and @Mod has no dist attribute before 1.20.6")
            }

            // 5. Minecraft API era — only where Minecraft names reach the production jar.
            if (loader == "neoforge" || unobfuscated) {
                if (mcAtLeast(mc, "1.21.2")) {
                    requireEra("net/minecraft/core/component/DataComponents",
                            "net/minecraft/world/item/ElytraItem",
                            "1.21.2 replaced ElytraItem/ArmorItem with data components")
                } else {
                    requireEra("net/minecraft/world/item/ElytraItem",
                            "net/minecraft/core/component/DataComponents",
                            "the GLIDER/EQUIPPABLE components only exist from 1.21.2")
                }
                if (mcAtLeast(mc, "26.1")) {
                    requireEra("net/minecraft/world/inventory/ContainerInput",
                            "net/minecraft/world/inventory/ClickType",
                            "26.1 renamed ClickType to ContainerInput")
                } else {
                    requireEra("net/minecraft/world/inventory/ClickType",
                            "net/minecraft/world/inventory/ContainerInput",
                            "ContainerInput only exists from 26.1")
                }
                if (mcAtLeast(mc, "1.21.9")) {
                    check(referenced("net/minecraft/client/KeyMapping\$Category"),
                            "1.21.9 replaced the String keybind category with KeyMapping.Category")
                }
            }
        }

        // Reports every failure rather than stopping at the first: across 44 jars, one
        // fix-and-rerun cycle per problem is the difference between minutes and an afternoon.
        val byLoader = jars.groupingBy { it.parentFile.name }.eachCount()
                .entries.sortedBy { it.key }.joinToString(", ") { "${it.value} ${it.key}" }
        logger.lifecycle("audit: ${jars.size} jars ($byLoader), $checksRun checks, ${failures.size} failed")
        if (failures.isNotEmpty()) {
            failures.forEach { logger.error("  !! $it") }
            throw GradleException("${failures.size} jar audit failures")
        }
    }
}

// Convenience runners for whichever version is currently active.
for (node in stonecutter.tree.nodes) {
    if (node.metadata != stonecutter.current || node.branch.id.isEmpty()) continue
    val loader = node.branch.id.replaceFirstChar { it.uppercaseChar() }
    for (type in listOf("Client", "Server")) tasks.register("runActive$type$loader") {
        group = "project"
        dependsOn("${node.hierarchy}run$type")
    }
}
