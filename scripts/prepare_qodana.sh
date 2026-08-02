#!/bin/sh
set -eu

mode="${1:-modern}"

case "$mode" in
  modern)
    ./gradlew --no-daemon \
      :common:testClasses \
      :fabric:compileClientJava \
      :fabric:testClasses \
      :neoforge:testClasses
    ;;
  legacy)
    ./neoforge-legacy/gradlew --no-daemon -p neoforge-legacy testClasses
    ;;
  *)
    echo "Unsupported Qodana preparation mode: $mode" >&2
    exit 2
    ;;
esac
