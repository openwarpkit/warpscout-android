#!/usr/bin/env bash
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
OUT="$ROOT/android/app/libs"
GOMOBILE_VERSION="v0.0.0-20260818145002-f020ddb2de58"

mkdir -p "$OUT"
go install "golang.org/x/mobile/cmd/gomobile@$GOMOBILE_VERSION"
gomobile init
gomobile bind \
  -target=android/arm64,android/arm,android/amd64 \
  -androidapi=26 \
  -ldflags="-X github.com/vernette/warpscout/mobileapi.coreVersion=${WARPSCOUT_CORE_VERSION:-dev} -X github.com/vernette/warpscout/mobileapi.upstreamVersion=${WARPSCOUT_UPSTREAM_TAG:-v0.16.0}" \
  -o "$OUT/warpscout.aar" \
  "$ROOT/mobileapi"
