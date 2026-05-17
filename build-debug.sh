#!/bin/bash
set -e

echo "=========================================="
echo "Refraction — Debug Build"
echo "Docker Image: mingc/android-build-box"
echo "CPU Limit: 2 cores | Memory Limit: 8GB"
echo "=========================================="

# Pull latest image if needed
sudo docker-compose pull android-build 2>/dev/null || true

# Run debug build with resource limits
sudo docker-compose run --rm android-build \
  ./gradlew assembleDebug \
  --no-daemon \
  --max-workers=2 \
  --build-cache \
  "$@"

echo ""
echo "=========================================="
echo "Build complete."
echo "APK location: app/build/outputs/apk/debug/"
echo "=========================================="
