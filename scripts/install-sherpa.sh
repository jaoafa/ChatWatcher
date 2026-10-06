#!/usr/bin/env bash
# sherpa-onnx is not on Maven Central. Build its Java and JNI APIs with token scores enabled.
set -euo pipefail
cd "$(dirname "$0")/.."
V=$(grep -oP '<sherpa.version>\K[^<]+' pom.xml)
SOURCE_COMMIT=11afbd009a7f8c08f4bcf2fc1b265d0df4670fbf
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
if [[ "$V" != "1.13.8" ]]; then
  echo "Sherpa-ONNX confidence patch is pinned to 1.13.8; update the source commit and patch before changing sherpa.version" >&2
  exit 1
fi

curl -fsSL -o "$tmp/source.tar.gz" "https://github.com/k2-fsa/sherpa-onnx/archive/$SOURCE_COMMIT.tar.gz"
mkdir "$tmp/source"
tar -xzf "$tmp/source.tar.gz" --strip-components=1 -C "$tmp/source"
git -C "$tmp/source" apply --unidiff-zero --check "$PWD/scripts/sherpa-confidence.patch"
git -C "$tmp/source" apply --unidiff-zero "$PWD/scripts/sherpa-confidence.patch"

cmake -S "$tmp/source" -B "$tmp/build" \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_INSTALL_PREFIX="$tmp/install" \
  -DBUILD_SHARED_LIBS=ON \
  -DSHERPA_ONNX_ENABLE_BINARY=OFF \
  -DSHERPA_ONNX_ENABLE_C_API=OFF \
  -DSHERPA_ONNX_ENABLE_JNI=ON \
  -DSHERPA_ONNX_ENABLE_PORTAUDIO=OFF \
  -DSHERPA_ONNX_ENABLE_SPEAKER_DIARIZATION=OFF \
  -DSHERPA_ONNX_ENABLE_TTS=OFF \
  -DSHERPA_ONNX_BUILD_C_API_EXAMPLES=OFF
cmake --build "$tmp/build" --parallel 2
cmake --install "$tmp/build"

mvn -q -B -f "$tmp/source/sherpa-onnx/java-api/pom.xml" -DskipTests package
mvn -q -B install:install-file \
  -Dfile="$tmp/source/sherpa-onnx/java-api/target/sherpa-onnx-jvm-$V.jar" \
  -DgroupId=com.k2fsa.sherpa.onnx -DartifactId=sherpa-onnx-jvm -Dversion="$V" -Dpackaging=jar

curl -fsSL -o "$tmp/native.jar" "https://github.com/k2-fsa/sherpa-onnx/releases/download/v$V/sherpa-onnx-native-lib-linux-x64-$V.jar"
mkdir "$tmp/native"
jar xf "$tmp/native.jar" -C "$tmp/native"
install -m 0644 "$tmp/install/lib/libsherpa-onnx-jni.so" \
  "$tmp/native/sherpa-onnx/native/linux-x64/libsherpa-onnx-jni.so"
jar uf "$tmp/native.jar" -C "$tmp/native" sherpa-onnx/native/linux-x64/libsherpa-onnx-jni.so
mvn -q -B install:install-file \
  -Dfile="$tmp/native.jar" \
  -DgroupId=com.k2fsa.sherpa.onnx -DartifactId=sherpa-onnx-native-lib-linux-x64 -Dversion="$V" -Dpackaging=jar
