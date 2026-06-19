# Elytra Swapper

**Elytra Swapper** is a Minecraft Fabric mod that allows players to quickly swap between an Elytra and a Chestplate. This mod is a fork of [ElytraChestplateSwapper](https://github.com/Saphjyr/ElytraChestplateSwapper/), with additional features and improvements.

## Features

- **Elytra and Chestplate Swapping**: Seamlessly swap between an Elytra and a Chestplate with a configurable keybinding.
- **Customizable Keybinding**: Set your preferred key for swapping in Minecraft's controls settings.
- **Enhanced Compatibility**: Improved functionality and compatibility with various Minecraft versions.

## Installation

1. **Install Fabric Loader**:
    - Download and install Fabric Loader from [Fabric's official website](https://fabricmc.net/use/).

2. **Install Fabric API**:
    - Download the Fabric API from [CurseForge](https://www.curseforge.com/minecraft/mc-mods/fabric-api) or [Modrinth](https://modrinth.com/mod/fabric-api).

3. **Add Elytra Swapper**:
    - Download the latest version of Elytra Swapper from the [Releases page](https://github.com/SaolGhra/ElytraSwapper/releases).
    - Place the JAR file into the `mods` folder in your Minecraft directory.

4. **Launch Minecraft**:
    - Start Minecraft using the Fabric profile. The mod should be active.

## Configuration

- **Keybinding**: By default, the key for swapping between Elytra and Chestplate is the grave accent (`` ` ``). You can customize this keybinding in Minecraft's controls settings under the "Elytra Swapper" category.

## Usage

1. **Equip Elytra or Chestplate**: Ensure that either an Elytra or a Chestplate is equipped in your inventory.
2. **Press the Keybinding**: Press the configured key to swap between the Elytra and Chestplate.

## Development

## CI Automation

Jenkins can poll for new stable Minecraft releases and attempt an automatic upgrade and release.

Required Jenkins setup:

- Create a string credential named `github-token` with a GitHub token that can push to the repository and create releases.
- Run the pipeline on a Linux agent with `git`, `curl`, Java 21, and a working Gradle environment.
- Keep the notification webhook at `https://notify.saolghra.co.uk/builds` reachable from the Jenkins agent.

Pipeline behavior:

- It checks Fabric metadata for the latest stable `1.x` Minecraft release.
- It updates `gradle.properties` with the new Minecraft, Fabric Loader, and Fabric API versions.
- It builds the mod, pushes the version bump back to the configured branch, tags the release, and uploads the built JAR to GitHub Releases.
- If the update build fails because the new Minecraft version needs code changes, the pipeline sends the same style of failure notification instead of publishing a release.

To contribute or modify the Elytra Swapper mod, follow these steps:

1. **Clone the Repository**:

   ```bash
   git clone https://github.com/SaolGhra/ElytraSwapper.git
