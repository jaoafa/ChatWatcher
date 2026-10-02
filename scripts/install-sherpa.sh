#!/usr/bin/env bash
# sherpa-onnx is not on Maven Central: install its release jars into the local Maven repository.
set -euo pipefail
cd "$(dirname "$0")/.."
V=$(grep -oP '<sherpa.version>\K[^<]+' pom.xml)
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
for a in sherpa-onnx-jvm sherpa-onnx-native-lib-linux-x64; do
  curl -fsSL -o "$tmp/$a.jar" "https://github.com/k2-fsa/sherpa-onnx/releases/download/v$V/$a-$V.jar"
  mvn -q -B install:install-file -Dfile="$tmp/$a.jar" -DgroupId=com.k2fsa.sherpa.onnx -DartifactId="$a" -Dversion="$V" -Dpackaging=jar
done
