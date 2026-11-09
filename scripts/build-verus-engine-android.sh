#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ENGINE="$ROOT/vendor/primo-arm-miner"
BUILD="$ROOT/.build-verus-android"
PREFIX="$BUILD/prefix"
SRC="$BUILD/src"
NDK="$ANDROID_HOME/ndk/28.2.13676358"
TOOLCHAIN="$NDK/build/cmake/android.toolchain.cmake"

if [[ ! -f "$TOOLCHAIN" ]]; then
  echo "ERROR: Android NDK toolchain not found: $TOOLCHAIN" >&2
  exit 1
fi

rm -rf "$BUILD"
mkdir -p "$SRC" "$PREFIX"

fetch_extract() {
  local url="$1" archive="$2" dir="$3"
  curl --fail --location --retry 3 "$url" -o "$SRC/$archive"
  tar -xf "$SRC/$archive" -C "$SRC"
  test -d "$SRC/$dir"
}

fetch_extract "https://github.com/Mbed-TLS/mbedtls/releases/download/mbedtls-3.6.2/mbedtls-3.6.2.tar.bz2" "mbedtls.tar.bz2" "mbedtls-3.6.2"
cmake -S "$SRC/mbedtls-3.6.2" -B "$BUILD/mbedtls" \
  -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN" -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=26 \
  -DCMAKE_INSTALL_PREFIX="$PREFIX" -DCMAKE_BUILD_TYPE=Release \
  -DUSE_SHARED_MBEDTLS_LIBRARY=OFF -DUSE_STATIC_MBEDTLS_LIBRARY=ON \
  -DENABLE_TESTING=OFF -DENABLE_PROGRAMS=OFF -DMBEDTLS_FATAL_WARNINGS=OFF
cmake --build "$BUILD/mbedtls" --parallel
cmake --install "$BUILD/mbedtls"

fetch_extract "https://github.com/akheron/jansson/releases/download/v2.14/jansson-2.14.tar.gz" "jansson.tar.gz" "jansson-2.14"
cmake -S "$SRC/jansson-2.14" -B "$BUILD/jansson" \
  -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN" -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=26 \
  -DCMAKE_INSTALL_PREFIX="$PREFIX" -DCMAKE_BUILD_TYPE=Release \
  -DJANSSON_BUILD_SHARED_LIBS=OFF -DJANSSON_BUILD_DOCS=OFF -DJANSSON_BUILD_TESTS=OFF
cmake --build "$BUILD/jansson" --parallel
cmake --install "$BUILD/jansson"

fetch_extract "https://curl.se/download/curl-8.11.1.tar.gz" "curl.tar.gz" "curl-8.11.1"
cmake -S "$SRC/curl-8.11.1" -B "$BUILD/curl" \
  -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN" -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=26 \
  -DCMAKE_INSTALL_PREFIX="$PREFIX" -DCMAKE_PREFIX_PATH="$PREFIX" \
  -DCMAKE_BUILD_TYPE=Release -DBUILD_SHARED_LIBS=OFF -DBUILD_CURL_EXE=OFF \
  -DBUILD_TESTING=OFF -DCURL_USE_MBEDTLS=ON -DCURL_USE_OPENSSL=OFF \
  -DCURL_USE_LIBSSH2=OFF -DCURL_USE_LIBPSL=OFF -DCURL_DISABLE_LDAP=ON \
  -DCURL_DISABLE_LDAPS=ON -DCURL_DISABLE_FTP=ON -DCURL_DISABLE_FILE=ON \
  -DCURL_DISABLE_TELNET=ON -DCURL_DISABLE_TFTP=ON -DCURL_DISABLE_DICT=ON \
  -DCURL_DISABLE_GOPHER=ON -DCURL_DISABLE_IMAP=ON -DCURL_DISABLE_MQTT=ON \
  -DCURL_DISABLE_POP3=ON -DCURL_DISABLE_RTSP=ON -DCURL_DISABLE_SMB=ON \
  -DCURL_DISABLE_SMTP=ON
cmake --build "$BUILD/curl" --parallel
cmake --install "$BUILD/curl"

bash "$ROOT/scripts/prepare-primo-engine.sh"

cmake -S "$ENGINE" -B "$BUILD/engine" \
  -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN" -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=26 \
  -DCMAKE_PREFIX_PATH="$PREFIX" -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_C_COMPILER_TARGET=aarch64-linux-android26 \
  -DCMAKE_CXX_COMPILER_TARGET=aarch64-linux-android26 \
  -DCMAKE_INSTALL_PREFIX="$BUILD/install" \
  -DPRIMO_RANDOMX=OFF -DPRIMO_PROFILE=generic -DPRIMO_LINKER=lld
cmake --build "$BUILD/engine" --parallel
cmake --install "$BUILD/engine"

mkdir -p "$ROOT/dist/verus-arm64"
cp "$BUILD/install/bin/primo-arm-miner" "$ROOT/dist/verus-arm64/libprimo.so"
chmod 755 "$ROOT/dist/verus-arm64/libprimo.so"

LIBCXX="$NDK/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so"
if [[ ! -f "$LIBCXX" ]]; then
  echo "ERROR: NDK libc++_shared.so not found: $LIBCXX" >&2
  exit 1
fi
cp "$LIBCXX" "$ROOT/dist/verus-arm64/libc++_shared.so"

curl --fail --location --retry 3 https://curl.se/ca/cacert.pem \
  -o "$ROOT/dist/verus-arm64/cacert.pem"

READELF="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-readelf"
"$READELF" -h "$ROOT/dist/verus-arm64/libprimo.so" | grep -q 'Machine:.*AArch64'
"$READELF" -d "$ROOT/dist/verus-arm64/libprimo.so" > "$ROOT/dist/verus-arm64/needed.txt"
if grep -E 'libcurl|libjansson|libmbed' "$ROOT/dist/verus-arm64/needed.txt"; then
  echo "ERROR: miner still depends on an unbundled shared dependency." >&2
  exit 1
fi
echo "Built ARM64 Verus engine. Runtime TLS requires PRIMO_CA_BUNDLE=.../cacert.pem."
