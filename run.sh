#!/bin/sh
KONA=$(cd "$(dirname "$0")" && pwd)
cd "${1:-.}" || exit 1
exec "${JAVA_HOME:-/usr}/bin/java" -cp "$KONA/lib/*" "$KONA/Kona.java"
