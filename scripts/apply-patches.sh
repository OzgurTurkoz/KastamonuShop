#!/usr/bin/env bash
# KastamonuShop — Applies surgical Android compatibility patches to upstream
# third_party/photocraft and third_party/craft-libs without breaking upstream updates.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

python3 - "$ROOT_DIR" <<'PY'
import pathlib, sys

root = pathlib.Path(sys.argv[1])

# 1. Disable CUPS printing crate (photocraft-ipp) on target_os = "android"
eng_cargo = root / "third_party/photocraft/crates/engine/Cargo.toml"
if eng_cargo.exists():
    txt = eng_cargo.read_text(encoding="utf-8")
    old = 'cfg(all(not(target_arch = "wasm32"), not(target_os = "windows")))'
    new = 'cfg(all(not(target_arch = "wasm32"), not(target_os = "windows"), not(target_os = "android")))'
    if old in txt:
        eng_cargo.write_text(txt.replace(old, new), encoding="utf-8")
        print("[PATCHED] photocraft/crates/engine/Cargo.toml (disabled photocraft-ipp on Android)")
    else:
        print("[OK] photocraft/crates/engine/Cargo.toml already patched")

print_rs = root / "third_party/photocraft/crates/engine/src/cmd/print_cmds.rs"
if print_rs.exists():
    txt = print_rs.read_text(encoding="utf-8")
    old1 = 'all(not(target_arch = "wasm32"), not(target_os = "windows"))'
    new1 = 'all(not(target_arch = "wasm32"), not(target_os = "windows"), not(target_os = "android"))'
    old2 = 'cfg(any(target_arch = "wasm32", target_os = "windows"))'
    new2 = 'cfg(any(target_arch = "wasm32", target_os = "windows", target_os = "android"))'
    changed = False
    if old1 in txt:
        txt = txt.replace(old1, new1)
        changed = True
    if old2 in txt:
        txt = txt.replace(old2, new2)
        changed = True
    if changed:
        print_rs.write_text(txt, encoding="utf-8")
        print("[PATCHED] photocraft/crates/engine/src/cmd/print_cmds.rs (Android IPP guard)")
    else:
        print("[OK] photocraft/crates/engine/src/cmd/print_cmds.rs already patched")

# 2. Enable native Android system font directories in craft-libs/crates/font-catalog/src/env.rs
font_env = root / "third_party/craft-libs/crates/font-catalog/src/env.rs"
if font_env.exists():
    txt = font_env.read_text(encoding="utf-8")
    if "Os::Android" not in txt:
        txt = txt.replace(
            "    /// Haiku.\n    Haiku,\n",
            "    /// Haiku.\n    Haiku,\n    /// Android (/system/fonts, /product/fonts, /data/fonts/files).\n    Android,\n",
            1,
        )
        txt = txt.replace(
            'if cfg!(target_arch = "wasm32") || cfg!(target_os = "android") || cfg!(target_os = "ios") {',
            'if cfg!(target_os = "android") {\n            Self::Android\n        } else if cfg!(target_arch = "wasm32") || cfg!(target_os = "ios") {',
            1,
        )
        txt = txt.replace(
            "        Os::None => return None,",
            "        Os::Android | Os::None => return None,",
        )
        txt = txt.replace(
            "        Os::None => {}\n",
            '        Os::Android => {\n            dirs.extend(["/system/fonts", "/product/fonts", "/system/product/fonts", "/data/fonts/files"].map(|d| env.rooted(d)));\n        }\n        Os::None => {}\n',
            1,
        )
        font_env.write_text(txt, encoding="utf-8")
        print("[PATCHED] craft-libs/crates/font-catalog/src/env.rs (enabled Os::Android font dirs)")
    else:
        print("[OK] craft-libs/crates/font-catalog/src/env.rs already patched")

print("All KastamonuShop Android compatibility patches verified.")
PY
