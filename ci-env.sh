#!/usr/bin/env sh
# Shared environment for every CI shell step. Source it, do not execute it:
#
#   . ./ci-env.sh
#
# Both paths below deliberately live outside the workspace. The pipeline runs cleanWs() after every
# build, so anything under $WORKSPACE is destroyed each time — which for this project means
# re-downloading a JDK plus Minecraft and every dependency for 23 versions, on every run, and makes
# a green build dependent on third-party uptime.
#
# They resolve here rather than in the Jenkinsfile's environment {} block because that block is
# interpolated on the controller. On a build agent that is a different machine, a controller-side
# path is meaningless. $HOME resolves on whichever machine actually runs the step.

export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$HOME/.gradle-elytraswapper}"
export JAVA_HOME="${JAVA_HOME_OVERRIDE:-$HOME/.jenkins-toolchains/temurin-25}"
export PATH="$JAVA_HOME/bin:$PATH"

mkdir -p "$GRADLE_USER_HOME"
