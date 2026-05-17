#!/bin/bash
set -e

echo "=========================================="
echo "Refraction — Release Build"
echo "Docker Image: mingc/android-build-box"
echo "CPU Limit: 2 cores | Memory Limit: 8GB"
echo "=========================================="

# Pull latest image if needed
sudo docker-compose pull android-build 2>/dev/null || true

# Run release build with resource limits
sudo docker-compose run --rm android-build \
  ./gradlew assembleRelease \
  --no-daemon \
  --max-workers=2 \
  --build-cache \
  "$@"

echo ""
echo "=========================================="
echo "Build complete."
echo "APK location: app/build/outputs/apk/release/"
echo "=========================================="
