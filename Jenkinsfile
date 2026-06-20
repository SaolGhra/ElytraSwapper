def parsePropertiesFile(String content) {
    Map<String, String> properties = [:]
    content.readLines().each { line ->
        def trimmed = line.trim()
        if (!trimmed || trimmed.startsWith('#') || !line.contains('=')) {
            return
        }

        int separatorIndex = line.indexOf('=')
        properties[line.substring(0, separatorIndex).trim()] = line.substring(separatorIndex + 1).trim()
    }
    properties
}

def replacePropertyLine(String content, String key, String value) {
    if (value == null) {
        error("Refusing to update ${key} with a null value.")
    }

    def lines = content.readLines()
    def updatedLines = []
    boolean replaced = false

    lines.each { line ->
        def trimmed = line.trim()
        if (!trimmed.startsWith('#') && line.contains('=')) {
            int separatorIndex = line.indexOf('=')
            def existingKey = line.substring(0, separatorIndex).trim()
            if (existingKey == key) {
                if (!replaced) {
                    updatedLines << "${key}=${value}"
                    replaced = true
                }
                return
            }
        }

        updatedLines << line
    }

    if (!replaced) {
        updatedLines << "${key}=${value}"
    }

    return updatedLines.join('\n') + '\n'
}

def nextModVersion(String currentModVersion, String minecraftVersion) {
    if (!currentModVersion) {
        return minecraftVersion
    }

    int separatorIndex = currentModVersion.lastIndexOf('-')
    if (separatorIndex >= 0) {
        return currentModVersion.substring(0, separatorIndex + 1) + minecraftVersion
    }

    return "${currentModVersion}-${minecraftVersion}"
}

def shellQuote(String value) {
    return "'${value.replace("'", "'\"'\"'")}'"
}

pipeline {
    agent {
        label 'linux'
    }

    parameters {
        string(name: 'BRANCH', defaultValue: 'master', description: 'Git branch Jenkins should build and update.')
        booleanParam(name: 'RUN_VERSION_UPDATE', defaultValue: false, description: 'Run a manual Minecraft dependency update before building.')
        string(name: 'TARGET_MINECRAFT_VERSION', defaultValue: '', description: 'Minecraft version to update to when RUN_VERSION_UPDATE is enabled (for example 1.21.10).')
        string(name: 'GITHUB_REPOSITORY', defaultValue: 'SaolGhra/ElytraSwapper', description: 'owner/repo used for pushing update commits.')
        string(name: 'GITHUB_TOKEN_CREDENTIALS_ID', defaultValue: 'github-token', description: 'Jenkins credential ID containing a GitHub token with repo push scope.')
        string(name: 'NOTIFY_URL', defaultValue: 'https://notify.saolghra.co.uk/builds', description: 'Webhook endpoint used for build notifications.')
    }

    environment {
        GRADLE_USER_HOME = '/home/jenkins/.gradle'
        _JAVA_OPTIONS = '-Xmx2G -Xms512M'
    }

    options {
        timestamps()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(daysToKeepStr: '365', numToKeepStr: '50'))
    }

    stages {
        stage('Preparation') {
            steps {
                ansiColor('xterm') {
                    echo "Running on node: ${env.NODE_NAME}"
                    sh 'chmod +x ./gradlew'
                    sh 'java -version || true'
                    sh './gradlew -version || gradle -version || true'
                }
            }
        }

        stage('Resolve Update Target') {
            steps {
                script {
                    def requestedTargetMcVersion = (params.TARGET_MINECRAFT_VERSION ?: '').trim()
                    def shouldRunManualUpdate = params.RUN_VERSION_UPDATE || !!requestedTargetMcVersion

                    def propertiesContent = readFile('gradle.properties')
                    def properties = parsePropertiesFile(propertiesContent)
                    def currentMcVersion = properties.minecraft_version ?: ''
                    def currentModVersion = properties.mod_version ?: ''

                    if (!currentMcVersion || !currentModVersion) {
                        error('gradle.properties is missing minecraft_version or mod_version.')
                    }

                    if (!shouldRunManualUpdate) {
                        writeFile file: '.jenkins-release.properties', text: [
                            mode: 'build-only',
                            current_mc_version: currentMcVersion,
                            target_mc_version: currentMcVersion,
                            current_mod_version: currentModVersion,
                            target_mod_version: currentModVersion,
                            target_loader_version: properties.loader_version ?: '',
                            target_fabric_version: properties.fabric_version ?: '',
                            target_yarn_mappings: properties.yarn_mappings ?: ''
                        ].collect { key, value -> "${key}=${value}" }.join('\n') + '\n'

                        currentBuild.description = "Build only (Minecraft ${currentMcVersion})"
                        echo "RUN_VERSION_UPDATE disabled; building current branch state without dependency updates."
                        return
                    }

                    if (!params.RUN_VERSION_UPDATE && requestedTargetMcVersion) {
                        echo 'TARGET_MINECRAFT_VERSION was provided, so manual update mode is enabled for this run.'
                    }

                    def targetMcVersion = requestedTargetMcVersion
                    if (!targetMcVersion) {
                        error('TARGET_MINECRAFT_VERSION is required when RUN_VERSION_UPDATE is enabled.')
                    }

                    def latestLoader = sh(
                        script: '''#!/bin/sh
set -eu
curl -fsSL https://meta.fabricmc.net/v2/versions/loader |
tr -d '[:space:]' |
grep -o '"version":"[^"]*","stable":true' |
sed 's/"version":"//;s/","stable":true//' |
head -n 1
''',
                        returnStdout: true
                    ).trim()
                    if (!latestLoader) {
                        error('Unable to determine the latest Fabric loader version from Fabric metadata.')
                    }

                    def latestYarnMappings = sh(
                        script: '''#!/bin/sh
set -eu
target_version=''' + shellQuote(targetMcVersion) + '''
latest_yarn=$(curl -fsSL "https://meta.fabricmc.net/v2/versions/yarn/${target_version}" |
tr -d '[:space:]' |
grep -o '"version":"[^"]*","stable":true' |
sed 's/"version":"//;s/","stable":true//' |
head -n 1 || true)
if [ -z "$latest_yarn" ]; then
    latest_yarn=$(curl -fsSL "https://meta.fabricmc.net/v2/versions/yarn/${target_version}" |
    tr -d '[:space:]' |
    grep -o '"version":"[^"]*"' |
    head -n 1 |
    sed 's/"version":"//;s/"$//' |
    grep -v '^$' || true)
fi
printf '%s' "$latest_yarn"
''',
                        returnStdout: true
                    ).trim()

                    def targetMappingsChannel = latestYarnMappings ? 'yarn' : 'mojang'
                    if (!latestYarnMappings) {
                        echo "No Yarn mappings found for Minecraft ${targetMcVersion}; falling back to Mojang mappings."
                    }

                    def latestFabricApi = sh(
                        script: '''#!/bin/sh
set -eu
target_version=''' + shellQuote(targetMcVersion) + '''
curl -fsSL https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml |
tr -d '[:space:]' |
grep -o '<version>[^<]*</version>' |
grep -F "+${target_version}</version>" |
sed 's#.*<version>##' |
sed 's#</version>.*##' |
tail -n 1
''',
                        returnStdout: true
                    ).trim()
                    if (!latestFabricApi) {
                        error("No Fabric API version published yet for Minecraft ${targetMcVersion}.")
                    }

                    def targetLoaderVersion = latestLoader
                    def targetFabricVersion = latestFabricApi
                    def targetYarnMappings = latestYarnMappings
                    def targetModVersion = nextModVersion(currentModVersion, targetMcVersion)

                    def updatedProperties = propertiesContent
                    updatedProperties = replacePropertyLine(updatedProperties, 'minecraft_version', targetMcVersion)
                    updatedProperties = replacePropertyLine(updatedProperties, 'mappings_channel', targetMappingsChannel)
                    if (targetMappingsChannel == 'yarn') {
                        updatedProperties = replacePropertyLine(updatedProperties, 'yarn_mappings', targetYarnMappings)
                    }
                    updatedProperties = replacePropertyLine(updatedProperties, 'loader_version', targetLoaderVersion)
                    updatedProperties = replacePropertyLine(updatedProperties, 'fabric_version', targetFabricVersion)
                    updatedProperties = replacePropertyLine(updatedProperties, 'mod_version', targetModVersion)
                    writeFile file: 'gradle.properties', text: updatedProperties

                    writeFile file: '.jenkins-release.properties', text: [
                        mode: 'manual-update',
                        current_mc_version: currentMcVersion,
                        target_mc_version: targetMcVersion,
                        current_mod_version: currentModVersion,
                        target_mod_version: targetModVersion,
                        target_mappings_channel: targetMappingsChannel,
                        target_loader_version: targetLoaderVersion,
                        target_fabric_version: targetFabricVersion,
                        target_yarn_mappings: targetYarnMappings
                    ].collect { key, value -> "${key}=${value}" }.join('\n') + '\n'

                    currentBuild.description = "Manual update ${currentMcVersion} -> ${targetMcVersion}"
                    def mappingsLabel = targetMappingsChannel == 'yarn' ? "Yarn ${targetYarnMappings}" : 'Mojang mappings'
                    echo "Prepared manual update ${currentMcVersion} -> ${targetMcVersion} using ${mappingsLabel}, loader ${targetLoaderVersion}, and Fabric API ${targetFabricVersion}."
                }
            }
        }

        stage('Build') {
            steps {
                script {
                    def releaseMetadata = parsePropertiesFile(readFile('.jenkins-release.properties'))
                    ansiColor('xterm') {
                        echo "Building Elytra Swapper branch ${params.BRANCH} for Minecraft ${releaseMetadata.target_mc_version ?: releaseMetadata.current_mc_version}..."
                        sh './gradlew clean build -x test'
                    }
                }
            }
        }

        stage('Archive') {
            steps {
                ansiColor('xterm') {
                    echo "Archiving built JARs for branch ${params.BRANCH}..."
                    archiveArtifacts artifacts: 'build/libs/*.jar', fingerprint: true
                }
            }
        }
    }

    post {
        always {
            script {
                def releaseMetadata = fileExists('.jenkins-release.properties') ? parsePropertiesFile(readFile('.jenkins-release.properties')) : [:]

                def requestedTargetMcVersion = (params.TARGET_MINECRAFT_VERSION ?: '').trim()
                def shouldRunManualUpdate = params.RUN_VERSION_UPDATE || !!requestedTargetMcVersion

                if (shouldRunManualUpdate) {
                    try {
                        String githubToken = null
                        try {
                            withCredentials([string(credentialsId: params.GITHUB_TOKEN_CREDENTIALS_ID, variable: 'GITHUB_TOKEN')]) {
                                githubToken = env.GITHUB_TOKEN
                            }
                        } catch (Exception ignored) {
                            echo "Credential ${params.GITHUB_TOKEN_CREDENTIALS_ID} is not Secret Text. Trying Username/Password credentials."
                        }

                        if (!githubToken) {
                            withCredentials([usernamePassword(credentialsId: params.GITHUB_TOKEN_CREDENTIALS_ID, usernameVariable: 'GITHUB_USERNAME', passwordVariable: 'GITHUB_TOKEN')]) {
                                githubToken = env.GITHUB_TOKEN
                            }
                        }

                        if (!githubToken) {
                            error("Unable to resolve a GitHub token from credentials ${params.GITHUB_TOKEN_CREDENTIALS_ID}.")
                        }

                        sh 'git config user.name "jenkins"'
                        sh 'git config user.email "jenkins@localhost"'
                        sh 'git add -A'

                        def hasStagedChanges = sh(script: 'git diff --cached --quiet', returnStatus: true) != 0
                        if (hasStagedChanges) {
                            def targetMcVersion = releaseMetadata.target_mc_version ?: params.TARGET_MINECRAFT_VERSION
                            def commitMessage = "chore: update Minecraft to ${targetMcVersion}"
                            sh "git commit -m ${shellQuote(commitMessage)}"

                            def remoteUrl = "https://x-access-token:${githubToken}@github.com/${params.GITHUB_REPOSITORY}.git"
                            def branchRefSpec = "HEAD:${params.BRANCH}"
                            sh "git remote set-url origin ${shellQuote(remoteUrl)}"
                            sh "git push origin ${shellQuote(branchRefSpec)}"

                            echo "Pushed manual update commit to ${params.GITHUB_REPOSITORY} (${params.BRANCH})."
                        } else {
                            echo 'RUN_VERSION_UPDATE was enabled but no file changes were produced; nothing to push.'
                        }
                    } catch (Exception pushError) {
                        currentBuild.result = 'FAILURE'
                        echo "Failed to push manual update changes: ${pushError.getMessage()}"
                    }
                }

                def finalResult = currentBuild.currentResult ?: 'SUCCESS'
                def attemptedVersion = releaseMetadata.target_mc_version ?: releaseMetadata.current_mc_version ?: params.TARGET_MINECRAFT_VERSION ?: 'unknown'
                def modeLabel = shouldRunManualUpdate ? 'manual update' : 'build-only run'

                ansiColor('xterm') {
                    if (finalResult == 'SUCCESS') {
                        echo "✅ ${modeLabel} completed successfully."
                    } else {
                        echo "❌ ${modeLabel} failed. Check console output for details."
                    }
                }

                def message = finalResult == 'SUCCESS'
                    ? "✅ Jenkins ${modeLabel} succeeded for ${env.JOB_NAME} #${env.BUILD_NUMBER} (Minecraft ${attemptedVersion}). ${env.BUILD_URL}"
                    : "❌ Jenkins ${modeLabel} failed for ${env.JOB_NAME} #${env.BUILD_NUMBER} while targeting Minecraft ${attemptedVersion}. ${env.BUILD_URL}"
                sh "curl -fsSL --retry 3 -X POST --data-binary ${shellQuote(message)} ${shellQuote(params.NOTIFY_URL)}"

                cleanWs()
            }
        }
    }
}
