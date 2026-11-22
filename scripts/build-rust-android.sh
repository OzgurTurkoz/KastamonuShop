#!/usr/bin/env bash
# KastamonuShop — Cross-compiles rust/kastamonushop-ffi for Android arm64-v8a
# and generates matching Kotlin UniFFI bindings directly into android/app.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUST_DIR="$ROOT_DIR/rust/kastamonushop-ffi"
JNI_LIBS="$ROOT_DIR/android/app/src/main/jniLibs"
KOTLIN_OUT="$ROOT_DIR/android/app/src/main/java"

export CRAFT_I18N_DIR="${CRAFT_I18N_DIR:-$ROOT_DIR/third_party/craft-i18n}"

echo "==> [1/3] Applying Android compatibility patches..."
bash "$ROOT_DIR/scripts/apply-patches.sh"

echo "==> [2/3] Building libkastamonushop_ffi.so for arm64-v8a (Android API 29+)..."
mkdir -p "$JNI_LIBS"
cd "$RUST_DIR"
cargo ndk -t arm64-v8a --platform 29 -o "$JNI_LIBS" build --release

echo "==> [3/3] Generating Kotlin UniFFI bindings..."
cargo run --bin uniffi-bindgen -- generate \
  --library "$JNI_LIBS/arm64-v8a/libkastamonushop_ffi.so" \
  --language kotlin \
  --out-dir "$KOTLIN_OUT"

echo "==> Done! Native library and Kotlin bindings are ready in android/app."
