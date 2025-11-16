#!/usr/bin/env bash

PLATFORMS=(
  "linux-x64"
  "linux-x64-musl"
  "linux-arm64"
  "linux-arm64-musl"
  "windows-x64"
  "darwin-x64"
  "darwin-arm64"
)

pnpm shadow-cljs release main

for PLATFORM in "${PLATFORMS[@]}"; do
  pnpm bun build --compile --minify --sourcemap --bytecode \
    --target=bun-$PLATFORM \
    --outfile compiled/secretary-$PLATFORM \
    out/secretary-cli.js
done
