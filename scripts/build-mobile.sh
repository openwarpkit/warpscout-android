#!/usr/bin/env bash
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
OUT="$ROOT/android/app/libs"
GOMOBILE_VERSION="v0.0.0-20240520174638-fa72addaaa1b"

mkdir -p "$OUT"
go install "golang.org/x/mobile/cmd/gomobile@$GOMOBILE_VERSION"
gomobile init
gomobile bind \
  -target=android/arm64,android/arm,android/amd64 \
  -androidapi=26 \
  -ldflags="-X github.com/vernette/warpscout/mobileapi.coreVersion=${WARPSCOUT_CORE_VERSION:-dev} -X github.com/vernette/warpscout/mobileapi.upstreamVersion=${WARPSCOUT_UPSTREAM_TAG:-v0.14.0}" \
  -o "$OUT/warpscout.aar" \
  "$ROOT/mobileapi"
