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
    def pattern = '(?m)^' + java.util.regex.Pattern.quote(key) + '=.*$'
    def replacement = "${key}=${java.util.regex.Matcher.quoteReplacement(value)}"
    def updated = content.replaceFirst(pattern, replacement)
    if (updated == content) {
        return content + (content.endsWith('\n') ? '' : '\n') + replacement + '\n'
    }
    updated
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

    triggers {
        cron('H H * * *')
    }

    parameters {
        string(name: 'BRANCH', defaultValue: 'master', description: 'Git branch Jenkins should build and update.')
        booleanParam(name: 'AUTO_UPDATE', defaultValue: true, description: 'Detect the latest stable Minecraft version and attempt to update automatically.')
        booleanParam(name: 'FORCE_RELEASE', defaultValue: false, description: 'Build and publish a release even if no new Minecraft version is detected.')
        string(name: 'GITHUB_REPOSITORY', defaultValue: 'SaolGhra/ElytraSwapper', description: 'owner/repo used for pushes and GitHub releases.')
        string(name: 'GITHUB_TOKEN_CREDENTIALS_ID', defaultValue: 'github-token', description: 'Jenkins string credential containing a GitHub token with repo scope.')
        string(name: 'RELEASE_TAG_PREFIX', defaultValue: 'v', description: 'Prefix used for git tags and GitHub releases.')
        string(name: 'NOTIFY_URL', defaultValue: 'https://notify.saolghra.co.uk/builds', description: 'Webhook endpoint used for build notifications.')
    }

    environment {
        GRADLE_USER_HOME = '/home/jenkins/.gradle'
        _JAVA_OPTIONS = '-Xmx2G -Xms512M'
        PIPELINE_ACTION = 'release'
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

        stage('Detect Version Update') {
            steps {
                script {
                    def propertiesContent = readFile('gradle.properties')
                    def properties = parsePropertiesFile(propertiesContent)
                    def currentMcVersion = properties.minecraft_version ?: ''
                    def currentModVersion = properties.mod_version ?: ''

                    if (!currentMcVersion || !currentModVersion) {
                        error('gradle.properties is missing minecraft_version or mod_version.')
                    }

                    if (!params.AUTO_UPDATE) {
                        def manualMetadata = [
                            current_mc_version: currentMcVersion,
                            target_mc_version: currentMcVersion,
                            target_loader_version: properties.loader_version ?: '',
                            target_fabric_version: properties.fabric_version ?: '',
                            target_yarn_mappings: properties.yarn_mappings ?: '',
                            current_mod_version: currentModVersion,
                            target_mod_version: currentModVersion,
                            release_tag: "${params.RELEASE_TAG_PREFIX}${currentModVersion}"
                        ]
                        writeFile file: '.jenkins-release.properties', text: manualMetadata.collect { key, value -> "${key}=${value}" }.join('\n') + '\n'
                        currentBuild.description = "Manual release for Minecraft ${currentMcVersion}"
                        echo "AUTO_UPDATE disabled; building current version ${currentMcVersion}."
                        return
                    }

                    def latestGame = sh(
                        script: '''#!/bin/sh
set -eu
current_version=''' + shellQuote(currentMcVersion) + '''
stable_versions=$(curl -fsSL https://meta.fabricmc.net/v2/versions/game |
tr -d '[:space:]' |
sed 's/},{/}\
{/g' |
grep '"stable":true' |
grep -Eo '"version":"[^"]+"' |
cut -d '"' -f 4 |
grep -Ev '(_unobfuscated|_original|-rc|-pre|snapshot)' || true)
if [ -z "$stable_versions" ]; then
    exit 1
fi
if printf '%s\n' "$stable_versions" | grep -Fx "$current_version" >/dev/null 2>&1; then
    next_version=$(printf '%s\n' "$stable_versions" | awk -v current="$current_version" 'prev != "" && $0 == current { print prev; exit } { prev = $0 }')
    if [ -z "$next_version" ]; then
        next_version="$current_version"
    fi
else
    next_version=$(printf '%s\n' "$stable_versions" | head -n 1)
fi
printf '%s' "$next_version"
''',
                        returnStdout: true
                    ).trim()
                    if (!latestGame) {
                        error('Unable to determine the latest stable Minecraft version from Fabric metadata.')
                    }

                    def latestLoader = sh(
                        script: '''#!/bin/sh
set -eu
curl -fsSL https://meta.fabricmc.net/v2/versions/loader |
tr -d '[:space:]' |
sed 's/},{/}\
{/g' |
grep '"stable":true' |
grep -Eo '"version":"[^"]+"' |
head -n 1 |
cut -d '"' -f 4
''',
                        returnStdout: true
                    ).trim()
                    if (!latestLoader) {
                        error('Unable to determine the latest Fabric loader version from Fabric metadata.')
                    }

                                        def latestYarnMappings = sh(
                                                script: '''#!/bin/sh
set -eu
target_version=''' + shellQuote(latestGame) + '''
latest_yarn=$(curl -fsSL "https://meta.fabricmc.net/v2/versions/yarn/${target_version}" |
tr -d '[:space:]' |
sed 's/},{/}\
{/g' |
grep '"stable":true' |
grep -Eo '"version":"[^"]+"' |
head -n 1 |
cut -d '"' -f 4 || true)
if [ -z "$latest_yarn" ]; then
    latest_yarn=$(curl -fsSL "https://meta.fabricmc.net/v2/versions/yarn/${target_version}" |
    tr -d '[:space:]' |
    sed 's/},{/}\
{/g' |
    grep -Eo '"version":"[^"]+"' |
    head -n 1 |
    cut -d '"' -f 4 || true)
fi
printf '%s' "$latest_yarn"
''',
                                                returnStdout: true
                                        ).trim()
                                        if (!latestYarnMappings) {
                                                error("Unable to determine the latest Yarn mappings for Minecraft ${latestGame}.")
                                        }

                    def latestFabricApi = sh(
                        script: '''#!/bin/sh
set -eu
target_version=''' + shellQuote(latestGame) + '''
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
                        error("No Fabric API version published yet for Minecraft ${latestGame}.")
                    }

                    def targetMcVersion = latestGame
                    def targetLoaderVersion = latestLoader
                    def targetFabricVersion = latestFabricApi
                    def targetYarnMappings = latestYarnMappings
                    def targetModVersion = nextModVersion(currentModVersion, latestGame)
                    def releaseTag = "${params.RELEASE_TAG_PREFIX}${targetModVersion}"

                    if (!targetMcVersion || !targetLoaderVersion || !targetFabricVersion || !targetYarnMappings || !targetModVersion) {
                        error('Failed to derive one or more target versions for the automatic update.')
                    }

                    if (currentMcVersion == targetMcVersion && !params.FORCE_RELEASE) {
                        env.PIPELINE_ACTION = 'skip'
                        writeFile file: '.jenkins-release.properties', text: [
                            current_mc_version: currentMcVersion,
                            target_mc_version: targetMcVersion,
                            target_loader_version: targetLoaderVersion,
                            target_fabric_version: targetFabricVersion,
                            target_yarn_mappings: targetYarnMappings,
                            current_mod_version: currentModVersion,
                            target_mod_version: targetModVersion,
                            release_tag: releaseTag
                        ].collect { key, value -> "${key}=${value}" }.join('\n') + '\n'
                        currentBuild.description = "Already on Minecraft ${currentMcVersion}"
                        echo "No new Minecraft version detected. Current version ${currentMcVersion} is already up to date."
                        return
                    }

                    def updatedProperties = propertiesContent
                    updatedProperties = replacePropertyLine(updatedProperties, 'minecraft_version', targetMcVersion)
                    updatedProperties = replacePropertyLine(updatedProperties, 'yarn_mappings', targetYarnMappings)
                    updatedProperties = replacePropertyLine(updatedProperties, 'loader_version', targetLoaderVersion)
                    updatedProperties = replacePropertyLine(updatedProperties, 'fabric_version', targetFabricVersion)
                    updatedProperties = replacePropertyLine(updatedProperties, 'mod_version', targetModVersion)
                    writeFile file: 'gradle.properties', text: updatedProperties
                    writeFile file: '.jenkins-release.properties', text: [
                        current_mc_version: currentMcVersion,
                        target_mc_version: targetMcVersion,
                        target_loader_version: targetLoaderVersion,
                        target_fabric_version: targetFabricVersion,
                        target_yarn_mappings: targetYarnMappings,
                        current_mod_version: currentModVersion,
                        target_mod_version: targetModVersion,
                        release_tag: releaseTag
                    ].collect { key, value -> "${key}=${value}" }.join('\n') + '\n'

                    currentBuild.description = "Targeting Minecraft ${targetMcVersion}"
                    echo "Prepared automatic update ${currentMcVersion} -> ${targetMcVersion} using Yarn ${targetYarnMappings}, loader ${targetLoaderVersion}, and Fabric API ${targetFabricVersion}."
                }
            }
        }

        stage('Build') {
            when {
                expression {
                    env.PIPELINE_ACTION == 'release'
                }
            }
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
            when {
                expression {
                    env.PIPELINE_ACTION == 'release'
                }
            }
            steps {
                ansiColor('xterm') {
                    echo "Archiving built JARs for branch ${params.BRANCH}..."
                    archiveArtifacts artifacts: 'build/libs/*.jar', fingerprint: true
                }
            }
        }

        stage('Publish Release') {
            when {
                expression {
                    env.PIPELINE_ACTION == 'release'
                }
            }
            steps {
                script {
                    def releaseMetadata = parsePropertiesFile(readFile('.jenkins-release.properties'))
                    withCredentials([string(credentialsId: params.GITHUB_TOKEN_CREDENTIALS_ID, variable: 'GITHUB_TOKEN')]) {
                        def branchRefSpec = "HEAD:${params.BRANCH}"
                        def releaseTag = releaseMetadata.release_tag
                        def targetMcVersion = releaseMetadata.target_mc_version
                        def targetLoaderVersion = releaseMetadata.target_loader_version
                        def targetFabricVersion = releaseMetadata.target_fabric_version
                        def targetModVersion = releaseMetadata.target_mod_version
                        def tagRef = "refs/tags/${releaseTag}"
                        def remoteUrl = "https://x-access-token:${GITHUB_TOKEN}@github.com/${params.GITHUB_REPOSITORY}.git"
                        def releaseName = "Elytra Swapper ${targetModVersion}"
                        def releaseBody = """Automated Jenkins release.

- Minecraft: ${targetMcVersion}
- Fabric Loader: ${targetLoaderVersion}
- Fabric API: ${targetFabricVersion}
- Source branch: ${params.BRANCH}
- Build: ${env.BUILD_URL}
""".stripIndent().trim()

                        if (sh(script: 'git diff --quiet', returnStatus: true) != 0) {
                            sh 'git config user.name "jenkins"'
                            sh 'git config user.email "jenkins@localhost"'
                            sh 'git add -u'
                            sh "git commit -m ${shellQuote("chore: update Minecraft to ${targetMcVersion}")}"
                        }

                        sh "git remote set-url origin ${shellQuote(remoteUrl)}"
                        sh "git push origin ${shellQuote(branchRefSpec)}"
                        sh "git tag -f ${shellQuote(releaseTag)}"
                        sh "git push origin ${shellQuote(tagRef)} --force"

                        def assetPath = sh(
                            script: "find build/libs -maxdepth 1 -type f -name '*.jar' ! -name '*-sources.jar' ! -name '*-dev.jar' | sort | head -n 1",
                            returnStdout: true
                        ).trim()
                        if (!assetPath) {
                            error('No release JAR found in build/libs.')
                        }
                        env.RELEASE_ASSET = assetPath

                        def releaseBodyJson = releaseBody
                            .replace('\\', '\\\\')
                            .replace('"', '\\"')
                            .replace('\n', '\\n')
                        def releasePayload = """{"tag_name":"${releaseTag}","target_commitish":"${params.BRANCH}","name":"${releaseName}","body":"${releaseBodyJson}","draft":false,"prerelease":false}"""
                        writeFile file: 'release-payload.json', text: releasePayload

                        def githubHeaders = "-H 'Authorization: Bearer ${GITHUB_TOKEN}' -H 'Accept: application/vnd.github+json' -H 'X-GitHub-Api-Version: 2022-11-28'"
                        def releaseTagApi = "https://api.github.com/repos/${params.GITHUB_REPOSITORY}/releases/tags/${releaseTag}"
                        def releaseLookupStatus = sh(
                            script: "curl -sS -o release-response.json -w '%{http_code}' ${githubHeaders} ${shellQuote(releaseTagApi)}",
                            returnStdout: true
                        ).trim()

                        if (releaseLookupStatus == '200') {
                            def existingReleaseId = sh(
                                script: "tr -d '\\n' < release-response.json | sed -n 's/.*\\\"id\\\":\\([0-9][0-9]*\\).*/\\1/p' | head -n 1",
                                returnStdout: true
                            ).trim()
                            if (!existingReleaseId) {
                                error('Failed to parse the existing GitHub release id.')
                            }
                            sh "curl -fsSL -X DELETE ${githubHeaders} ${shellQuote("https://api.github.com/repos/${params.GITHUB_REPOSITORY}/releases/${existingReleaseId}")}"
                            sh "curl -fsSL -X POST ${githubHeaders} -H 'Content-Type: application/json' ${shellQuote("https://api.github.com/repos/${params.GITHUB_REPOSITORY}/releases")} --data @release-payload.json > release-response.json"
                        } else if (releaseLookupStatus == '404') {
                            sh "curl -fsSL -X POST ${githubHeaders} -H 'Content-Type: application/json' ${shellQuote("https://api.github.com/repos/${params.GITHUB_REPOSITORY}/releases")} --data @release-payload.json > release-response.json"
                        } else {
                            error("Unexpected GitHub release lookup status: ${releaseLookupStatus}")
                        }

                        def assetName = assetPath.substring(assetPath.lastIndexOf('/') + 1)
                        def uploadUrl = sh(
                            script: "tr -d '\\n' < release-response.json | sed -n 's/.*\\\"upload_url\\\":\\\"\\([^\\\"]*\\)\\\".*/\\1/p' | sed 's/{?name,label}//' | head -n 1",
                            returnStdout: true
                        ).trim()
                        if (!uploadUrl) {
                            error('Failed to parse the GitHub release upload URL.')
                        }
                        sh "curl -fsSL -X POST ${githubHeaders} -H 'Content-Type: application/java-archive' --data-binary @${shellQuote(assetPath)} ${shellQuote("${uploadUrl}?name=${assetName}")} > /dev/null"

                        echo "Published GitHub release ${releaseTag} with asset ${assetName}."
                    }
                }
            }
        }
    }

    post {
        success {
            script {
                def releaseMetadata = fileExists('.jenkins-release.properties') ? parsePropertiesFile(readFile('.jenkins-release.properties')) : [:]
                ansiColor('xterm') {
                    if (env.PIPELINE_ACTION == 'skip') {
                        echo "No new Minecraft version detected. Release was skipped."
                    } else {
                        echo "✅ Automated update and release completed successfully."
                    }
                }

                def message = env.PIPELINE_ACTION == 'skip'
                    ? "ℹ️ Jenkins checked ${env.JOB_NAME} #${env.BUILD_NUMBER}: no new Minecraft version was available beyond ${releaseMetadata.current_mc_version ?: 'unknown'}."
                    : "✅ Jenkins released ${env.JOB_NAME} #${env.BUILD_NUMBER} for Minecraft ${releaseMetadata.target_mc_version ?: 'unknown'}. ${env.BUILD_URL}"
                sh "curl -fsSL --retry 3 -X POST --data-urlencode ${shellQuote("message=${message}")} ${shellQuote(params.NOTIFY_URL)}"
                cleanWs()
            }
        }
        failure {
            script {
                def releaseMetadata = fileExists('.jenkins-release.properties') ? parsePropertiesFile(readFile('.jenkins-release.properties')) : [:]
                ansiColor('xterm') {
                    echo "❌ Automated update or release failed. Check console output for details."
                }

                def attemptedVersion = releaseMetadata.target_mc_version ?: releaseMetadata.current_mc_version ?: 'unknown'
                def message = "❌ Jenkins failed for ${env.JOB_NAME} #${env.BUILD_NUMBER} while targeting Minecraft ${attemptedVersion}. ${env.BUILD_URL}"
                sh "curl -fsSL --retry 3 -X POST --data-urlencode ${shellQuote("message=${message}")} ${shellQuote(params.NOTIFY_URL)}"
                cleanWs()
            }
        }
    }
}