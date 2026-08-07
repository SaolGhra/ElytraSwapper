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

stonecutter active "1.21.11" /* [SC] DO NOT EDIT */

// Build every enabled version into build/libs/{version}/{loader}.
//
// Always go through a chiseled task. Stonecutter only materialises a version's source while that
// version is the active build target, so invoking `:loader:version:build` directly produces an
// EMPTY jar for any version that is not currently active — which looks like a successful build.
stonecutter registerChiseled tasks.register("chiseledBuild", stonecutter.chiseled) {
    group = "project"
    ofTask("buildAndCollect")
}

// Publishes every enabled version. Dry-run unless MODRINTH_TOKEN is set.
stonecutter registerChiseled tasks.register("chiseledPublish", stonecutter.chiseled) {
    group = "project"
    ofTask("publishMods")
}

// Per-loader chiseled builds, so a single loader can be rebuilt without the whole matrix.
for (branch in stonecutter.tree.branches) {
    if (branch.id.isEmpty()) continue
    val loader = branch.id.replaceFirstChar { it.uppercaseChar() }
    stonecutter registerChiseled tasks.register("chiseledBuild$loader", stonecutter.chiseled) {
        group = "project"
        versions { b, _ -> b == branch.id }
        ofTask("buildAndCollect")
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
