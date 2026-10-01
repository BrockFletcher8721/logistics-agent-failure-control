#!/usr/bin/env sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
CLASSES=$(mktemp -d)
trap 'rm -rf "$CLASSES"' EXIT

javac -d "$CLASSES" $(find "$ROOT/src/main/java" -name '*.java')
cp "$ROOT/src/main/resources/application.properties" "$CLASSES/application.properties"
java -cp "$CLASSES" dev.infrai.logistics.LogisticsAgentApplication
