#!/usr/bin/env bash
# Builds every Minecraft version on every loader that supports it, one version per Gradle invocation.
#
# Deliberately NOT `gradlew chiseledBuild`. Stonecutter keeps a single physical copy of src/ and
# rewrites it in place as the active version changes, and the loader projects compile those sources
# directly. A chiseled run switches versions mid-invocation, so loader nodes race the switch and
# compile the wrong API era — which shows up as a compile error on a later version rather than
# anything obviously ordering-related. One active version per JVM makes that impossible.
#
# Costs an extra JVM start per version. Worth it for a build that is either correct or loudly broken.
#
#   ./buildMatrix.sh            build everything
#   ./buildMatrix.sh 1.21.11    build a single version
set -uo pipefail
cd "$(dirname "$0")"

ALL_VERSIONS=(1.20 1.20.1 1.20.2 1.20.3 1.20.4 1.20.5 1.20.6
              1.21 1.21.1 1.21.2 1.21.3 1.21.4 1.21.5 1.21.6
              1.21.7 1.21.8 1.21.9 1.21.10 1.21.11
              26.1 26.1.1 26.1.2 26.2)

# NeoForge was forked from Forge at 1.20.2 and does not exist before it.
neoforge_supported() { [[ "$1" != "1.20" && "$1" != "1.20.1" ]]; }

VERSIONS=("${@:-}")
[[ -z "${VERSIONS[0]:-}" ]] && VERSIONS=("${ALL_VERSIONS[@]}")

GRADLE=(./gradlew --console=plain)
PASS=(); FAIL=()

for v in "${VERSIONS[@]}"; do
    echo "=============================================================== $v"
    if ! "${GRADLE[@]}" -q "Set active project to $v" >/dev/null 2>&1; then
        echo "  !! could not make $v active"; FAIL+=("$v:set-active"); continue
    fi

    targets=(":fabric:$v:buildAndCollect")
    neoforge_supported "$v" && targets+=(":neoforge:$v:buildAndCollect")

    if "${GRADLE[@]}" "${targets[@]}"; then
        echo "  -- $v ok"; PASS+=("$v")
    else
        echo "  !! $v FAILED"; FAIL+=("$v")
    fi
done

echo
echo "==============================================================="
echo "matrix: ${#PASS[@]} passed, ${#FAIL[@]} failed"
[[ ${#FAIL[@]} -gt 0 ]] && { printf 'failed: %s\n' "${FAIL[*]}"; exit 1; }

# A green run that produced nothing is not a green run.
count=$(find build/libs -name '*.jar' 2>/dev/null | wc -l)
echo "collected $count jars under build/libs/"
[[ "$count" -eq 0 ]] && { echo "!! no jars collected"; exit 1; }
exit 0
