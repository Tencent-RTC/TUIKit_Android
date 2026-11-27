#!/usr/bin/env bash
#
# Build libatomicengine.so from AtomicEngine source and place it into
# atomicengine/src/main/jniLibs/<abi>/ so the :atomic_engine source module can
# package it into the APK.
#
# Owned by Chat Demo's :atomicEngineSource. Does NOT invoke
# atomic_engine's Gradle assemble (that would recurse into this Demo build).
# Native compile is gn/ninja copy_engine_sdk only.
#
# Hardened for Android Studio local usage:
#   - prefers /usr/bin python that has the _ssl module (clang update.py)
#   - makes brew tools (gn/ninja) discoverable when launched from Studio
#   - wraps `python` (do not symlink python3 as `python`: macOS stub -> 2.7)
#   - shims stdlib `pipes` (removed in 3.13+)
#   - auto-selects NDK r22 (GNU binutils); when the GNU prefixed tools crash on
#     this host, GN's android_ndk_root is pointed at a project-local overlay
#     under out/ that symlinks the real NDK and maps only the prefixed
#     binutils to llvm-*. The shared Android SDK/NDK is never modified.
#
# Usage: build_atomic_engine_so.sh <engine_path> <sdk_path> [abis] [ndk_version_hint]
#   abis: comma separated, default "armeabi-v7a,arm64-v8a"

set -eo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ENGINE_PATH="$1"
SDK_PATH="$2"
ABIS="${3:-armeabi-v7a,arm64-v8a}"
NDK_VERSION_HINT="${4:-}"
TAG="[atomic-engine]"

if [ -z "$ENGINE_PATH" ] || [ -z "$SDK_PATH" ]; then
  echo "$TAG usage: $0 <engine_path> <sdk_path> [abis] [ndk_version_hint]" >&2
  exit 2
fi

# Studio Gradle daemon often starts with a minimal PATH.
if [ -f "$HOME/.bash_profile" ]; then source "$HOME/.bash_profile" >/dev/null 2>&1 || true; fi
if [ -f "$HOME/.zprofile" ]; then source "$HOME/.zprofile" >/dev/null 2>&1 || true; fi
# /usr/bin first: platform/Homebrew python without _ssl crashes clang update.py.
export PATH="/usr/bin:/opt/homebrew/bin:/usr/local/bin:$PATH"
for dt in "$ENGINE_PATH/depot_tools" "$ENGINE_PATH/../depot_tools" "$HOME/depot_tools"; do
  if [ -d "$dt" ]; then
    export PATH="$PATH:$dt"
    break
  fi
done

require() { command -v "$1" >/dev/null 2>&1 || { echo "$TAG required tool not found: $1" >&2; exit 1; }; }
require gn
require ninja
require python3

echo "$TAG python check:"
python3 -c "import ssl, sys; print('$TAG python OK:', sys.executable)"

PY3="$(command -v python3)"
PY3="$(cd "$(dirname "$PY3")" && pwd)/$(basename "$PY3")"

# gn gen needs pkg_resources; ninja actions need python3 + pipes (removed in 3.13+).
# Do NOT `ln -s python3 python`: on macOS, /usr/bin/python3 is a stub that dispatches
# to Python 2.7 when argv[0] is `python`. Use a wrapper so argv[0] stays python3.
SHIM_DIR="$ENGINE_PATH/out/.gnpyshim"
mkdir -p "$SHIM_DIR"
rm -f "$SHIM_DIR/python" "$SHIM_DIR/pipes.py" "$SHIM_DIR/pipes.pyc"
rm -rf "$SHIM_DIR/__pycache__"
cat > "$SHIM_DIR/python" <<EOF
#!/bin/bash
exec "$PY3" "\$@"
EOF
chmod +x "$SHIM_DIR/python"
if ! "$SHIM_DIR/python" -c "import sys; assert sys.version_info[0] >= 3" >/dev/null 2>&1; then
  echo "$TAG python shim resolved to Python 2 (macOS python3 stub + argv[0]=python)." >&2
  echo "$TAG wrapper target: $PY3" >&2
  exit 1
fi
cat > "$SHIM_DIR/pipes.py" <<'EOF'
"""Shim for stdlib pipes removed in Python 3.13+."""
import re

_find_unsafe = re.compile(r'[^\w@%+=:,./-]').search

def quote(s):
    if not s:
        return "''"
    if _find_unsafe(s) is None:
        return s
    return "'" + s.replace("'", "'\"'\"'") + "'"
EOF

GEN_PYTHON="$PY3"
if ! "$GEN_PYTHON" -c "import pkg_resources" >/dev/null 2>&1; then
  echo "$TAG no python3 with pkg_resources found for 'gn gen'." >&2
  echo "$TAG install one, e.g.: python3 -m pip install --user setuptools" >&2
  exit 1
fi

run_ninja() {
  PATH="$SHIM_DIR:$PATH" PYTHONPATH="$SHIM_DIR:${PYTHONPATH:-}" ninja "$@"
}
run_gn_gen() {
  local genshim="$ENGINE_PATH/out/.gngenpyshim"
  mkdir -p "$genshim"
  rm -f "$genshim/python"
  cat > "$genshim/python" <<EOF
#!/bin/bash
exec "$GEN_PYTHON" "\$@"
EOF
  chmod +x "$genshim/python"
  PATH="$genshim:$PATH" gn gen "$1"
}

ndk_is_usable() {
  local d="$1"
  ls "$d"/toolchains/llvm/prebuilt/*/bin/clang >/dev/null 2>&1 || return 1
  ls "$d"/toolchains/llvm/prebuilt/*/bin/aarch64-linux-android-ar >/dev/null 2>&1 || return 1
  return 0
}
resolve_ndk() {
  local base="$SDK_PATH/ndk" v d
  if [ -n "$NDK_VERSION_HINT" ] && ndk_is_usable "$base/$NDK_VERSION_HINT"; then
    echo "$base/$NDK_VERSION_HINT"; return 0
  fi
  for v in 22.1.7171670 21.4.7075529 21.3.6528147 20.1.5948944; do
    if ndk_is_usable "$base/$v"; then echo "$base/$v"; return 0; fi
  done
  for d in "$base"/*; do
    if ndk_is_usable "$d"; then echo "$d"; return 0; fi
  done
  return 1
}
NDK_ROOT="$(resolve_ndk)" || {
  echo "$TAG no NDK with both clang and GNU binutils (aarch64-linux-android-ar) found." >&2
  echo "$TAG AtomicEngine's GN toolchain is tuned for r22; please install 22.1.7171670:" >&2
  echo "$TAG   sdkmanager \"ndk;22.1.7171670\"" >&2
  exit 1
}

# NDK r22 ships GNU binutils (<prefix>-ar etc.) that crash on some macOS hosts.
# Never write into the shared NDK: when a prefixed ar crashes, build a
# project-local overlay under out/ that symlinks the real NDK (sysroot and all)
# and maps only the prefixed ar/nm/readelf/strip/ranlib/objcopy/objdump to
# their target-agnostic llvm-* twins. GN then receives the overlay as
# android_ndk_root. The real NDK directory stays untouched.
prefixed_ar_crashes() {
  local arpath="$1" tdir rc
  tdir="$(mktemp -d)"
  printf 'x' > "$tdir/m.o"
  ( "$arpath" -T -r -c -s -D "$tdir/o.a" "$tdir/m.o" ) >/dev/null 2>&1
  rc=$?
  rm -rf "$tdir"
  [ "$rc" -ne 0 ]
}

build_ndk_overlay() {
  local overlay="$1" entry name hostdir host prefix tool
  rm -rf "$overlay"
  mkdir -p "$overlay/toolchains/llvm/prebuilt"
  for entry in "$NDK_ROOT"/*; do
    name="$(basename "$entry")"
    [ "$name" = "toolchains" ] && continue
    ln -sfn "$entry" "$overlay/$name"
  done
  for entry in "$NDK_ROOT/toolchains"/*; do
    name="$(basename "$entry")"
    [ "$name" = "llvm" ] && continue
    ln -sfn "$entry" "$overlay/toolchains/$name"
  done
  for entry in "$NDK_ROOT/toolchains/llvm"/*; do
    name="$(basename "$entry")"
    [ "$name" = "prebuilt" ] && continue
    ln -sfn "$entry" "$overlay/toolchains/llvm/$name"
  done
  for hostdir in "$NDK_ROOT/toolchains/llvm/prebuilt"/*; do
    host="$(basename "$hostdir")"
    mkdir -p "$overlay/toolchains/llvm/prebuilt/$host/bin"
    for entry in "$hostdir"/*; do
      name="$(basename "$entry")"
      [ "$name" = "bin" ] && continue
      ln -sfn "$entry" "$overlay/toolchains/llvm/prebuilt/$host/$name"
    done
    for entry in "$hostdir/bin"/*; do
      ln -sfn "$entry" "$overlay/toolchains/llvm/prebuilt/$host/bin/$(basename "$entry")"
    done
    for prefix in aarch64-linux-android arm-linux-androideabi x86_64-linux-android i686-linux-android; do
      for tool in ar nm readelf strip ranlib objcopy objdump; do
        [ -e "$hostdir/bin/$prefix-$tool" ] || continue
        [ -x "$hostdir/bin/llvm-$tool" ] || continue
        ln -sfn "$hostdir/bin/llvm-$tool" "$overlay/toolchains/llvm/prebuilt/$host/bin/$prefix-$tool"
      done
    done
  done
}

GN_NDK_ROOT="$NDK_ROOT"
ndk_needs_overlay=0
for bindir in "$NDK_ROOT"/toolchains/llvm/prebuilt/*/bin; do
  [ -d "$bindir" ] || continue
  for prefix in aarch64-linux-android arm-linux-androideabi x86_64-linux-android i686-linux-android; do
    arpath="$bindir/$prefix-ar"
    [ -e "$arpath" ] || continue
    if prefixed_ar_crashes "$arpath"; then
      ndk_needs_overlay=1
      break 2
    fi
  done
done
if [ "$ndk_needs_overlay" -eq 1 ]; then
  GN_NDK_ROOT="$ENGINE_PATH/out/.ndk-overlay/$(basename "$NDK_ROOT")"
  build_ndk_overlay "$GN_NDK_ROOT"
  echo "$TAG GNU prefixed binutils crash on this host; GN android_ndk_root uses project-local overlay: $GN_NDK_ROOT" >&2
fi

echo "$TAG engine : $ENGINE_PATH"
echo "$TAG sdk    : $SDK_PATH"
echo "$TAG ndk    : $NDK_ROOT"
if [ "$GN_NDK_ROOT" != "$NDK_ROOT" ]; then echo "$TAG ndk overlay: $GN_NDK_ROOT"; fi
echo "$TAG abis   : $ABIS"

cd "$ENGINE_PATH"
SETUP="compile/configuration/setup_gn_args.py"
COPY_TARGET="src/engine/platform_adapter/kotlin:copy_engine_sdk"
SDK_VERSION="${sdk_version:-5.0.0.0}"
chmod +x "$SETUP" >/dev/null 2>&1 || true

abi_target_os() {
  case "$1" in
    arm64-v8a)   echo "android" ;;
    armeabi-v7a) echo "android_arm" ;;
    x86_64)      echo "android_x64" ;;
    x86)         echo "android_x86" ;;
    *)           echo "" ;;
  esac
}
abi_out_dir() {
  case "$1" in
    arm64-v8a)   echo "out/atomic_engine_release_android" ;;
    armeabi-v7a) echo "out/atomic_engine_release_android_arm" ;;
    x86_64)      echo "out/atomic_engine_release_android_x64" ;;
    x86)         echo "out/atomic_engine_release_android_x86" ;;
    *)           echo "" ;;
  esac
}

# GN links libliteavsdk from sdk/android/trtc/libs/<abi>, includes LiteAV headers
# from sdk/android/trtc/include and IM from sdk/android/im/{libs,include}. These are
# compile/link inputs only; the runtime libliteavsdk.so comes from the
# LiteAVSDK_Professional Maven AAR.
sync_trtc_include() {
  local base="$ENGINE_PATH/sdk/android/trtc"
  local fallback
  mkdir -p "$base/include"
  if [ -d "$base/libs/include" ] && [ -n "$(ls -A "$base/libs/include" 2>/dev/null)" ]; then
    cp -rf "$base/libs/include/." "$base/include/"
  fi
  if [ -n "$(ls -A "$base/include" 2>/dev/null)" ]; then
    return 0
  fi
  fallback="$ENGINE_PATH/../tuikit_engine/sdk/android/trtc/include"
  if [ -d "$fallback" ] && [ -n "$(ls -A "$fallback" 2>/dev/null)" ]; then
    echo "$TAG using tuikit_engine LiteAV include: $fallback"
    cp -rf "$fallback/." "$base/include/"
  fi
}

dependency_sdk_ready() {
  local base="$ENGINE_PATH/sdk/android" d abi
  for d in "$base/im/libs" "$base/im/include" "$base/trtc/libs"; do
    [ -d "$d" ] && [ -n "$(ls -A "$d" 2>/dev/null)" ] || return 1
  done
  sync_trtc_include
  [ -n "$(ls -A "$base/trtc/include" 2>/dev/null)" ] || return 1
  IFS=',' read -ra _abis <<< "$ABIS"
  for abi in "${_abis[@]}"; do
    abi="$(echo "$abi" | tr -d '[:space:]')"
    [ -z "$abi" ] && continue
    [ -f "$base/trtc/libs/$abi/libliteavsdk.so" ] || return 1
  done
  return 0
}

prepare_dependency_sdk() {
  if dependency_sdk_ready; then
    echo "$TAG dependency sdk ready under sdk/android"
    return 0
  fi
  echo "$TAG dependency sdk is missing or incomplete under $ENGINE_PATH/sdk/android" >&2
  echo "$TAG required (compile/link inputs, not downloaded by this script):" >&2
  echo "$TAG   sdk/android/im/libs/<abi>/libImSDK.so and sdk/android/im/include/*.h" >&2
  echo "$TAG   sdk/android/trtc/libs/<abi>/libliteavsdk.so and sdk/android/trtc/include/*.h" >&2
  echo "$TAG copy an existing populated sdk/android into the engine checkout, then rebuild." >&2
  exit 1
}

prepare_dependency_sdk

IFS=',' read -ra ABI_ARR <<< "$ABIS"
for abi in "${ABI_ARR[@]}"; do
  abi="$(echo "$abi" | tr -d '[:space:]')"
  [ -z "$abi" ] && continue
  os="$(abi_target_os "$abi")"
  out="$(abi_out_dir "$abi")"
  if [ -z "$os" ]; then
    echo "$TAG skip unknown abi: $abi" >&2
    continue
  fi
  if [ ! -f "$ENGINE_PATH/sdk/android/trtc/libs/$abi/libliteavsdk.so" ]; then
    echo "$TAG skip $abi: sdk/android/trtc/libs/$abi/libliteavsdk.so missing (LiteAV 13.x usually ships arm64-v8a / armeabi-v7a only)" >&2
    continue
  fi
  echo "$TAG ===== $abi ($os) -> $out ====="

  python3 "$SETUP" --target-os "$os" --sdk-version "$SDK_VERSION" \
    --android-sdk-root "$SDK_PATH" --android-ndk-root "$GN_NDK_ROOT"
  run_gn_gen "$out" >/dev/null

  echo "$TAG compiling libatomicengine.so for $abi ..."
  mkdir -p out
  if ! run_ninja -C "$out" "$COPY_TARGET" > "out/.build_${abi}.log" 2>&1; then
    echo "$TAG native build failed for $abi:" >&2
    grep -vE "SyntaxWarning|re\.compile|invalid escape sequence|Did you mean" "out/.build_${abi}.log" | tail -60 >&2
    exit 1
  fi
  echo "$TAG OK: $abi"
done

echo "$TAG all done. jniLibs:"
ls -1 src/atomic/kotlin/atomicengine/src/main/jniLibs/*/libatomicengine.so 2>/dev/null || true
