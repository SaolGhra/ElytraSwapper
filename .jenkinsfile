import groovy.json.JsonOutput
import groovy.json.JsonSlurperClassic

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
        string(name: 'BRANCH', defaultValue: 'main', description: 'Git branch Jenkins should build and update.')
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
        CURRENT_MC_VERSION = ''
        TARGET_MC_VERSION = ''
        TARGET_LOADER_VERSION = ''
        TARGET_FABRIC_VERSION = ''
        CURRENT_MOD_VERSION = ''
        TARGET_MOD_VERSION = ''
        RELEASE_TAG = ''
        RELEASE_ASSET = ''
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
                    sh 'java -version || true'
                    sh './gradlew -version || gradle -version || true'
                    git branch: params.BRANCH, url: "https://github.com/${params.GITHUB_REPOSITORY}.git"
                    sh 'chmod +x ./gradlew'
                }
            }
        }

        stage('Detect Version Update') {
            steps {
                script {
                    def propertiesContent = readFile('gradle.properties')
                    def properties = parsePropertiesFile(propertiesContent)

                    env.CURRENT_MC_VERSION = properties.minecraft_version ?: ''
                    env.CURRENT_MOD_VERSION = properties.mod_version ?: ''
                    env.TARGET_LOADER_VERSION = properties.loader_version ?: ''
                    env.TARGET_FABRIC_VERSION = properties.fabric_version ?: ''

                    if (!params.AUTO_UPDATE) {
                        env.TARGET_MC_VERSION = env.CURRENT_MC_VERSION
                        env.TARGET_MOD_VERSION = env.CURRENT_MOD_VERSION
                        env.RELEASE_TAG = "${params.RELEASE_TAG_PREFIX}${env.TARGET_MOD_VERSION}"
                        currentBuild.description = "Manual release for Minecraft ${env.TARGET_MC_VERSION}"
                        echo "AUTO_UPDATE disabled; building current version ${env.TARGET_MC_VERSION}."
                        return
                    }

                    def jsonSlurper = new JsonSlurperClassic()
                    def gameVersions = jsonSlurper.parseText(sh(script: 'curl -fsSL https://meta.fabricmc.net/v2/versions/game', returnStdout: true).trim())
                    def loaderVersions = jsonSlurper.parseText(sh(script: 'curl -fsSL https://meta.fabricmc.net/v2/versions/loader', returnStdout: true).trim())
                    def apiMetadata = new XmlSlurper().parseText(sh(script: 'curl -fsSL https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml', returnStdout: true).trim())

                    def latestGame = gameVersions.find { entry ->
                        entry.stable && (entry.version ==~ /^1\.[0-9]+(\.[0-9]+)?$/)
                    }?.version
                    if (!latestGame) {
                        error('Unable to determine the latest stable Minecraft version from Fabric metadata.')
                    }

                    def latestLoader = loaderVersions.find { entry -> entry.stable }?.version ?: loaderVersions[0]?.version
                    if (!latestLoader) {
                        error('Unable to determine the latest Fabric loader version from Fabric metadata.')
                    }

                    def matchingApiVersions = []
                    apiMetadata.versioning.versions.version.each { versionNode ->
                        def version = versionNode.text()
                        if (version.endsWith("+${latestGame}")) {
                            matchingApiVersions << version
                        }
                    }
                    if (!matchingApiVersions) {
                        error("No Fabric API version published yet for Minecraft ${latestGame}.")
                    }

                    def latestFabricApi = matchingApiVersions.last()

                    env.TARGET_MC_VERSION = latestGame
                    env.TARGET_LOADER_VERSION = latestLoader
                    env.TARGET_FABRIC_VERSION = latestFabricApi
                    env.TARGET_MOD_VERSION = nextModVersion(env.CURRENT_MOD_VERSION, latestGame)
                    env.RELEASE_TAG = "${params.RELEASE_TAG_PREFIX}${env.TARGET_MOD_VERSION}"

                    if (env.CURRENT_MC_VERSION == latestGame && !params.FORCE_RELEASE) {
                        env.PIPELINE_ACTION = 'skip'
                        currentBuild.description = "Already on Minecraft ${env.CURRENT_MC_VERSION}"
                        echo "No new Minecraft version detected. Current version ${env.CURRENT_MC_VERSION} is already up to date."
                        return
                    }

                    def updatedProperties = propertiesContent
                    updatedProperties = replacePropertyLine(updatedProperties, 'minecraft_version', env.TARGET_MC_VERSION)
                    updatedProperties = replacePropertyLine(updatedProperties, 'loader_version', env.TARGET_LOADER_VERSION)
                    updatedProperties = replacePropertyLine(updatedProperties, 'fabric_version', env.TARGET_FABRIC_VERSION)
                    updatedProperties = replacePropertyLine(updatedProperties, 'mod_version', env.TARGET_MOD_VERSION)
                    writeFile file: 'gradle.properties', text: updatedProperties

                    currentBuild.description = "Targeting Minecraft ${env.TARGET_MC_VERSION}"
                    echo "Prepared automatic update ${env.CURRENT_MC_VERSION} -> ${env.TARGET_MC_VERSION} using loader ${env.TARGET_LOADER_VERSION} and Fabric API ${env.TARGET_FABRIC_VERSION}."
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
                ansiColor('xterm') {
                    echo "Building Elytra Swapper branch ${params.BRANCH} for Minecraft ${env.TARGET_MC_VERSION ?: env.CURRENT_MC_VERSION}..."
                    sh './gradlew clean build -x test'
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
                    withCredentials([string(credentialsId: params.GITHUB_TOKEN_CREDENTIALS_ID, variable: 'GITHUB_TOKEN')]) {
                        def jsonSlurper = new JsonSlurperClassic()
                        def branchRefSpec = "HEAD:${params.BRANCH}"
                        def tagRef = "refs/tags/${env.RELEASE_TAG}"
                        def remoteUrl = "https://x-access-token:${GITHUB_TOKEN}@github.com/${params.GITHUB_REPOSITORY}.git"
                        def releaseName = "Elytra Swapper ${env.TARGET_MOD_VERSION}"
                        def releaseBody = """Automated Jenkins release.

- Minecraft: ${env.TARGET_MC_VERSION}
- Fabric Loader: ${env.TARGET_LOADER_VERSION}
- Fabric API: ${env.TARGET_FABRIC_VERSION}
- Source branch: ${params.BRANCH}
- Build: ${env.BUILD_URL}
""".stripIndent().trim()

                        if (sh(script: 'git diff --quiet -- gradle.properties', returnStatus: true) != 0) {
                            sh 'git config user.name "jenkins"'
                            sh 'git config user.email "jenkins@localhost"'
                            sh 'git add gradle.properties'
                            sh "git commit -m ${shellQuote("chore: update Minecraft to ${env.TARGET_MC_VERSION}")}"
                        }

                        sh "git remote set-url origin ${shellQuote(remoteUrl)}"
                        sh "git push origin ${shellQuote(branchRefSpec)}"
                        sh "git tag -f ${shellQuote(env.RELEASE_TAG)}"
                        sh "git push origin ${shellQuote(tagRef)} --force"

                        def assetPath = sh(
                            script: "find build/libs -maxdepth 1 -type f -name '*.jar' ! -name '*-sources.jar' ! -name '*-dev.jar' | sort | head -n 1",
                            returnStdout: true
                        ).trim()
                        if (!assetPath) {
                            error('No release JAR found in build/libs.')
                        }
                        env.RELEASE_ASSET = assetPath

                        def payload = [
                            tag_name: env.RELEASE_TAG,
                            target_commitish: params.BRANCH,
                            name: releaseName,
                            body: releaseBody,
                            draft: false,
                            prerelease: false
                        ]
                        writeFile file: 'release-payload.json', text: JsonOutput.toJson(payload)

                        def githubHeaders = "-H 'Authorization: Bearer ${GITHUB_TOKEN}' -H 'Accept: application/vnd.github+json' -H 'X-GitHub-Api-Version: 2022-11-28'"
                        def releaseTagApi = "https://api.github.com/repos/${params.GITHUB_REPOSITORY}/releases/tags/${env.RELEASE_TAG}"
                        def releaseLookupStatus = sh(
                            script: "curl -sS -o release-response.json -w '%{http_code}' ${githubHeaders} ${shellQuote(releaseTagApi)}",
                            returnStdout: true
                        ).trim()

                        def releaseData
                        if (releaseLookupStatus == '200') {
                            releaseData = jsonSlurper.parseText(readFile('release-response.json'))
                            sh "curl -fsSL -X PATCH ${githubHeaders} -H 'Content-Type: application/json' ${shellQuote("https://api.github.com/repos/${params.GITHUB_REPOSITORY}/releases/${releaseData.id}")} --data @release-payload.json > release-response.json"
                            releaseData = jsonSlurper.parseText(readFile('release-response.json'))
                        } else if (releaseLookupStatus == '404') {
                            sh "curl -fsSL -X POST ${githubHeaders} -H 'Content-Type: application/json' ${shellQuote("https://api.github.com/repos/${params.GITHUB_REPOSITORY}/releases")} --data @release-payload.json > release-response.json"
                            releaseData = jsonSlurper.parseText(readFile('release-response.json'))
                        } else {
                            error("Unexpected GitHub release lookup status: ${releaseLookupStatus}")
                        }

                        def assetName = assetPath.substring(assetPath.lastIndexOf('/') + 1)
                        def existingAsset = (releaseData.assets ?: []).find { asset -> asset.name == assetName }
                        if (existingAsset) {
                            sh "curl -fsSL -X DELETE ${githubHeaders} ${shellQuote("https://api.github.com/repos/${params.GITHUB_REPOSITORY}/releases/assets/${existingAsset.id}")}"
                        }

                        def uploadUrl = releaseData.upload_url.replace('{?name,label}', '')
                        sh "curl -fsSL -X POST ${githubHeaders} -H 'Content-Type: application/java-archive' --data-binary @${shellQuote(assetPath)} ${shellQuote("${uploadUrl}?name=${assetName}")} > /dev/null"

                        echo "Published GitHub release ${env.RELEASE_TAG} with asset ${assetName}."
                    }
                }
            }
        }
    }

    post {
        success {
            script {
                ansiColor('xterm') {
                    if (env.PIPELINE_ACTION == 'skip') {
                        echo "No new Minecraft version detected. Release was skipped."
                    } else {
                        echo "✅ Automated update and release completed successfully."
                    }
                }

                def message = env.PIPELINE_ACTION == 'skip'
                    ? "ℹ️ Jenkins checked ${env.JOB_NAME} #${env.BUILD_NUMBER}: no new Minecraft version was available beyond ${env.CURRENT_MC_VERSION}."
                    : "✅ Jenkins released ${env.JOB_NAME} #${env.BUILD_NUMBER} for Minecraft ${env.TARGET_MC_VERSION}. ${env.BUILD_URL}"
                sh "curl -fsSL --retry 3 -X POST --data-urlencode ${shellQuote("message=${message}")} ${shellQuote(params.NOTIFY_URL)}"
            }
        }
        failure {
            script {
                ansiColor('xterm') {
                    echo "❌ Automated update or release failed. Check console output for details."
                }

                def attemptedVersion = env.TARGET_MC_VERSION ?: env.CURRENT_MC_VERSION ?: 'unknown'
                def message = "❌ Jenkins failed for ${env.JOB_NAME} #${env.BUILD_NUMBER} while targeting Minecraft ${attemptedVersion}. ${env.BUILD_URL}"
                sh "curl -fsSL --retry 3 -X POST --data-urlencode ${shellQuote("message=${message}")} ${shellQuote(params.NOTIFY_URL)}"
            }
        }
        always {
            cleanWs()
        }
    }
}