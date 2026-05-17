#!/bin/bash

# Fallback wrapper script for environments without gradle-wrapper.jar
# The Docker build image (mingc/android-build-box) includes Gradle system-wide

set -e

APP_HOME="$(cd "$(dirname "$0")" && pwd)"

if command -v gradle >/dev/null 2>&1; then
    exec gradle "$@"
else
    echo "ERROR: Gradle not found. Please install Gradle or run from the Docker container."
    exit 1
fi
