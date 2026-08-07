// ElytraSwapper CI — builds the whole Stonecutter matrix, verifies it, then asks before releasing.
//
// The shape is deliberate: a push builds and verifies every Minecraft version on every loader and
// stops. Publishing is never automatic. If everything passes, the pipeline pauses on an input step
// and waits to be told to release; nobody has to remember to run a second job, and nothing ships
// without someone having looked at the results.
//
// Progress goes to ntfy as it happens so a long matrix run can be followed from a phone.

// Never fails the build — a notification problem is not a build problem. Values go through the
// environment so a changelog containing quotes cannot break or inject into the shell.
def notify(String title, String message, String tags = 'gear', String priority = 'default') {
    if (!params.NOTIFY_URL?.trim()) { return }
    withEnv(["NTFY_TITLE=${title}", "NTFY_BODY=${message}", "NTFY_TAGS=${tags}", "NTFY_PRIO=${priority}"]) {
        sh '''
            curl -sS -X POST \
                -H "Title: $NTFY_TITLE" -H "Tags: $NTFY_TAGS" -H "Priority: $NTFY_PRIO" \
                -d "$NTFY_BODY" "''' + params.NOTIFY_URL + '''" >/dev/null 2>&1 || true
        '''
    }
}

pipeline {
    agent { label 'linux' }

    parameters {
        booleanParam(name: 'RUN_UNIT_TESTS', defaultValue: true,
                description: 'Run the version-independent unit tests. Seconds.')
        booleanParam(name: 'RUN_JAR_AUDIT', defaultValue: true,
                description: 'Static audit of every built jar: entrypoint present, metadata ' +
                        'templated, java target correct, no stale guard branches. No game launch.')
        booleanParam(name: 'ASK_TO_RELEASE', defaultValue: true,
                description: 'When the build and all checks pass, pause and ask whether to publish. ' +
                        'Untick for an unattended verify-only run.')
        booleanParam(name: 'PUBLISH_GITHUB', defaultValue: true,
                description: 'Included in the release when approved: GitHub release with every jar.')
        booleanParam(name: 'PUBLISH_MODRINTH', defaultValue: true,
                description: 'Included in the release when approved: upload every jar to Modrinth.')
        text(name: 'CHANGELOG', defaultValue: '',
                description: 'Release notes. Used for the GitHub release body and as the Modrinth ' +
                        'changelog on every uploaded version. Markdown.')
        string(name: 'NOTIFY_URL', defaultValue: 'https://notify.saolghra.co.uk/builds',
                description: 'ntfy topic for progress notifications. Empty disables them.')
    }

    options {
        timestamps()
        ansiColor('xterm')
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '50', daysToKeepStr: '365'))
    }

    environment {
        _JAVA_OPTIONS = '-Xmx3G -Xms512M'
    }

    triggers { pollSCM('H/5 * * * *') }

    stages {
        stage('Provision JDK') {
            steps {
                script { notify("ElytraSwapper #${env.BUILD_NUMBER} started", 'build + verify', 'hammer') }
                // The matrix spans Java 17 (1.20-1.20.4), 21 (1.20.5-1.21.11) and 25 (26.x). Gradle
                // provisions the per-node compilers itself via the foojay resolver; this only has to
                // supply a JVM new enough to RUN Gradle 9.5, which means 25.
                sh '''
                    set -e
                    . ./ci-env.sh
                    if [ ! -x "$JAVA_HOME"/bin/javac ]; then
                        mkdir -p "$JAVA_HOME"
                        curl -sSL "https://api.adoptium.net/v3/binary/latest/25/ga/linux/x64/jdk/hotspot/normal/eclipse" \
                            -o "$JAVA_HOME/../jdk25.tar.gz"
                        tar -xzf "$JAVA_HOME/../jdk25.tar.gz" -C "$JAVA_HOME" --strip-components=1
                        rm -f "$JAVA_HOME/../jdk25.tar.gz"
                    fi
                    chmod +x gradlew buildMatrix.sh publishMatrix.sh
                    ./gradlew --version
                '''
            }
        }

        stage('Build matrix') {
            steps {
                // Retried because the upstream mod mavens are not reliable, and a single transient
                // artifact failure otherwise reds a matrix that takes a long time to rebuild.
                retry(2) {
                    sh '''
                        set -e
                        . ./ci-env.sh
                        # One Gradle invocation per Minecraft version. NOT chiseledBuild: Stonecutter
                        # rewrites a single physical copy of src/ as the active version changes, and
                        # the loader projects compile those sources, so switching versions inside one
                        # invocation races the switch and silently compiles the wrong API era.
                        ./buildMatrix.sh
                    '''
                }
                script {
                    def jars = sh(script: 'ls build/libs/*/*/*.jar 2>/dev/null | wc -l', returnStdout: true).trim()
                    notify("Build OK — ${jars} jars", 'matrix built, starting checks', 'package')
                }
            }
        }

        stage('Unit tests') {
            when { expression { return params.RUN_UNIT_TESTS } }
            steps {
                sh '''
                    set -e
                    . ./ci-env.sh
                    # The slot arithmetic and the equip rules have no Minecraft types in them, so one
                    # node covers every version. The node still compiles the common sources, so the
                    # version has to be made active first or Stonecutter has the wrong era on disk.
                    ./gradlew --console=plain -q "Set active project to 1.21.11"
                    ./gradlew :1.21.11:test --stacktrace
                '''
            }
            post {
                always { junit allowEmptyResults: true, testResults: '**/build/test-results/test/*.xml' }
                failure { script { notify('Unit tests FAILED', "build #${env.BUILD_NUMBER}", 'x', 'high') } }
            }
        }

        stage('Audit jars') {
            when { expression { return params.RUN_JAR_AUDIT } }
            steps {
                sh '''
                    set -e
                    . ./ci-env.sh
                    mkdir -p build
                    # NOT `gradlew ... | tee`: the exit status of a pipeline is the status of its
                    # LAST command, so a piped-into-tee failure reports as success and the stage
                    # goes green over a failed audit.
                    if ./gradlew auditJars --stacktrace > build/audit.txt 2>&1; then
                        cat build/audit.txt
                    else
                        cat build/audit.txt
                        exit 1
                    fi
                '''
                script {
                    def line = sh(script: "grep -E '^audit:' build/audit.txt | tail -1 || true",
                                  returnStdout: true).trim()
                    notify("Jar audit: ${line}", "build #${env.BUILD_NUMBER}",
                           line.contains('0 failed') ? 'white_check_mark' : 'x',
                           line.contains('0 failed') ? 'default' : 'high')
                }
            }
            post { always { archiveArtifacts artifacts: 'build/audit.txt', allowEmptyArchive: true } }
        }

        stage('Archive') {
            steps {
                archiveArtifacts artifacts: 'build/libs/**/*.jar', fingerprint: true,
                                 excludes: '**/*-sources.jar'
            }
        }

        // Everything above has passed by the time we get here. Ask, rather than assuming.
        stage('Release?') {
            when { expression { return params.ASK_TO_RELEASE } }
            steps {
                script {
                    def jars = sh(script: 'ls build/libs/*/*/*.jar 2>/dev/null | wc -l', returnStdout: true).trim()
                    notify("Ready to release — approval needed",
                           "${jars} jars built and verified. ${env.BUILD_URL}input", 'question', 'high')
                    // Times out rather than pinning an executor forever. Aborting on timeout is the
                    // safe default: not releasing is always recoverable, releasing is not.
                    timeout(time: 12, unit: 'HOURS') {
                        input message: "Publish ${jars} jars?", ok: 'Release'
                    }
                    env.DO_RELEASE = 'true'
                }
            }
        }

        stage('Publish to GitHub') {
            when { expression { return env.DO_RELEASE == 'true' && params.PUBLISH_GITHUB } }
            steps {
                script { notify('Publishing to GitHub…', 'release for the current mod version', 'rocket') }
                // Through a file, not the command line: release notes are multi-line and contain
                // quotes and backticks, which would be mangled or would break the shell.
                writeFile file: 'build/changelog.md', text: params.CHANGELOG ?: ''
                withCredentials([string(credentialsId: 'github-token', variable: 'GH_TOKEN')]) {
                    sh '''
                        set -e
                        VERSION=$(grep -E '^mod\\.version=' gradle.properties | cut -d= -f2)
                        REPO="SaolGhra/ElytraSwapper"
                        TAG="v$VERSION"

                        # Refuse rather than silently create a second release for an existing tag.
                        EXISTING=$(curl -sS -o /dev/null -w '%{http_code}' \
                            -H "Authorization: Bearer $GH_TOKEN" \
                            "https://api.github.com/repos/$REPO/releases/tags/$TAG")
                        if [ "$EXISTING" = "200" ]; then
                            echo "!! release $TAG already exists — bump mod.version or delete it first"
                            exit 1
                        fi

                        command -v python3 >/dev/null 2>&1 || { apt-get update -qq && apt-get install -y -qq python3 >/dev/null; }
                        python3 - "$TAG" "$VERSION" > build/release.json <<'PY'
import json, sys
tag, version = sys.argv[1], sys.argv[2]
body = open('build/changelog.md').read().strip() or f'ElytraSwapper {version}'
print(json.dumps({'tag_name': tag, 'name': f'ElytraSwapper {version}',
                  'body': body, 'draft': False, 'prerelease': False}))
PY
                        UPLOAD=$(curl -sS -X POST \
                            -H "Authorization: Bearer $GH_TOKEN" -H "Content-Type: application/json" \
                            -d @build/release.json "https://api.github.com/repos/$REPO/releases" \
                            | python3 -c "import json,sys; print(json.load(sys.stdin)['upload_url'].split('{')[0])")

                        COUNT=0
                        for jar in build/libs/*/*/*.jar; do
                            case "$jar" in *-sources.jar) continue;; esac
                            curl -sS -X POST -H "Authorization: Bearer $GH_TOKEN" \
                                -H "Content-Type: application/java-archive" \
                                --data-binary @"$jar" "$UPLOAD?name=$(basename "$jar")" >/dev/null
                            COUNT=$((COUNT+1))
                        done
                        echo "attached $COUNT jars to $TAG"
                    '''
                }
                script { notify('GitHub release published', 'check the releases page', 'white_check_mark') }
            }
        }

        stage('Publish to Modrinth') {
            when { expression { return env.DO_RELEASE == 'true' && params.PUBLISH_MODRINTH } }
            steps {
                script { notify('Publishing to Modrinth…', 'uploading the matrix', 'rocket') }
                writeFile file: 'build/changelog.md', text: params.CHANGELOG ?: ''
                withCredentials([string(credentialsId: 'modrinth-token', variable: 'MODRINTH_TOKEN')]) {
                    sh '''
                        set -e
                        . ./ci-env.sh
                        # Same one-version-per-invocation rule as the build, for the same reason.
                        ./publishMatrix.sh
                    '''
                }
                script { notify('Modrinth publish done', 'every version uploaded', 'white_check_mark') }
            }
        }
    }

    post {
        success {
            script {
                notify("ElytraSwapper #${env.BUILD_NUMBER} SUCCESS",
                       "${currentBuild.durationString.replace(' and counting', '')}", 'white_check_mark')
            }
        }
        failure {
            script { notify("ElytraSwapper #${env.BUILD_NUMBER} FAILED", "${env.BUILD_URL}", 'rotating_light', 'high') }
        }
        aborted {
            script { notify("ElytraSwapper #${env.BUILD_NUMBER} not released", 'release declined or timed out', 'no_entry') }
        }
        always { cleanWs() }
    }
}
