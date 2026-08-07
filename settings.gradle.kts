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

        // NeoForge was forked from Forge at 1.20.2 and simply does not exist for 1.20/1.20.1 — no
        // build configuration can produce a jar there. See .claude/docs/loader-support-matrix.md.
        branch("neoforge") {
            versions("26.2", "1.21.11")
        }

        // Forge covers 1.20/1.20.1 where NeoForge cannot, which is the only reason it is here.
        // (Forge does now publish across the whole range, but a second Forge-like loader for
        // versions NeoForge already covers would double the matrix for no user-visible gain.)
        branch("forge") {
            versions("1.20.1")
        }
    }
}

rootProject.name = "ElytraSwapper"
