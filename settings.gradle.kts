pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/")
        maven("https://maven.architectury.dev")
        maven("https://maven.minecraftforge.net")
        maven("https://maven.neoforged.net/releases/")
        maven("https://maven.kikugie.dev/releases")
        maven("https://maven.kikugie.dev/snapshots")
    }
}

plugins {
    // The tree spans Java 17 (1.20-1.20.4), 21 (1.20.5-1.21.11) and 25 (26.x). Very few machines
    // have all three, and CI agents have none reliably, so let Gradle provision the missing ones
    // rather than requiring them to be installed out of band.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("dev.kikugie.stonecutter") version "0.6"
}

stonecutter {
    centralScript = "build.gradle.kts"
    kotlinController = true

    create(rootProject) {
        // Root `src/` is the loader-agnostic common source; each loader branch adds only its
        // entrypoint. Versions are staged deliberately: ACTIVE is what currently builds and is
        // proven, PENDING is the rest of the required 1.20 -> 26.2 range, enabled a group at a time
        // as each API era is ported. The end state is every version below enabled.
        versions(
            // -- unobfuscated era (no mappings, Java 25, loom-no-remap) ------------------------
            "26.2",
            // -- component era, obfuscated (Mojmap, Java 21, loom-remap) -----------------------
            "1.21.11",
            // -- pre-component era (Mojmap, Java 17, loom-remap) -------------------------------
            "1.20.1",
        )

        // Fabric is the only loader with unbroken coverage of all 23 releases, so it inherits every
        // root version.
        branch("fabric")

        // NeoForge was forked from Forge at 1.20.2 and does not exist for 1.20/1.20.1 — no build
        // configuration can produce a jar there. Forge is deliberately NOT used to fill that gap:
        // those two versions ship Fabric (and therefore Quilt) only.
        branch("neoforge") {
            versions("26.2", "1.21.11")
        }
    }
}

rootProject.name = "ElytraSwapper"
