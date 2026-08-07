#!/usr/bin/env bash
# Publishes every built version to Modrinth, one Gradle invocation per Minecraft version.
#
# Same one-version-per-invocation rule as buildMatrix.sh, and for the same reason: Stonecutter
# rewrites a single physical copy of src/ as the active version changes, so anything that switches
# versions mid-invocation races it.
#
# Requires MODRINTH_TOKEN in the environment. Without it the publish plugin runs dry and uploads
# nothing, which is the safe default — a missing credential should not silently look like success,
# so this refuses up front instead.
set -uo pipefail
cd "$(dirname "$0")"

if [[ -z "${MODRINTH_TOKEN:-}" ]]; then
    echo "!! MODRINTH_TOKEN is not set — refusing to run a publish that would upload nothing"
    exit 1
fi

ALL_VERSIONS=(1.20 1.20.1 1.20.2 1.20.3 1.20.4 1.20.5 1.20.6
              1.21 1.21.1 1.21.2 1.21.3 1.21.4 1.21.5 1.21.6
              1.21.7 1.21.8 1.21.9 1.21.10 1.21.11
              26.1 26.1.1 26.1.2 26.2)

VERSIONS=("${@:-}")
[[ -z "${VERSIONS[0]:-}" ]] && VERSIONS=("${ALL_VERSIONS[@]}")

CHANGELOG_ARG=()
[[ -s build/changelog.md ]] && CHANGELOG_ARG=(-Pchangelog="$(cat build/changelog.md)")

PASS=(); FAIL=()
for v in "${VERSIONS[@]}"; do
    echo "=============================================================== $v"
    if ! ./gradlew --console=plain -q "Set active project to $v" >/dev/null 2>&1; then
        echo "  !! could not make $v active"; FAIL+=("$v"); continue
    fi
    if ./gradlew --console=plain "${CHANGELOG_ARG[@]}" ":fabric:$v:publishMods" \
        $([[ "$v" != "1.20" && "$v" != "1.20.1" ]] && echo ":neoforge:$v:publishMods"); then
        PASS+=("$v")
    else
        echo "  !! $v FAILED"; FAIL+=("$v")
    fi
done

echo
echo "publish: ${#PASS[@]} succeeded, ${#FAIL[@]} failed"
# Deliberately fails loudly on a partial publish: half a release on Modrinth is worse than none,
# and it needs someone to look rather than being retried blindly.
[[ ${#FAIL[@]} -gt 0 ]] && { printf 'failed: %s\n' "${FAIL[*]}"; exit 1; }
exit 0
