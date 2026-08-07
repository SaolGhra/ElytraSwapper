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
        // entrypoint. Every release from 1.20 to 26.2 inclusive.
        versions(
            "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.20.5", "1.20.6", "1.21",
            "1.21.1", "1.21.2", "1.21.3", "1.21.4", "1.21.5", "1.21.6", "1.21.7", "1.21.8",
            "1.21.9", "1.21.10", "1.21.11", "26.1", "26.1.1", "26.1.2", "26.2",
        )

        // Fabric is the only loader with unbroken coverage of all 23 releases, so it inherits every
        // root version.
        branch("fabric")

        // NeoForge was forked from Forge at 1.20.2 and does not exist for 1.20/1.20.1 — no build
        // configuration can produce a jar there. Forge is deliberately NOT used to fill that gap:
        // those two versions ship Fabric (and therefore Quilt) only.
        branch("neoforge") {
            versions(
                "1.20.2", "1.20.3", "1.20.4", "1.20.5", "1.20.6",
                "1.21", "1.21.1", "1.21.2", "1.21.3", "1.21.4", "1.21.5", "1.21.6",
                "1.21.7", "1.21.8", "1.21.9", "1.21.10", "1.21.11",
                "26.1", "26.1.1", "26.1.2", "26.2",
            )
        }
    }
}

rootProject.name = "ElytraSwapper"
