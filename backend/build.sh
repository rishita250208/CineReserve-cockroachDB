#!/usr/bin/env sh
set -eu
BACKEND_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
JDBC_JAR=${CINERESERVE_JDBC_JAR:-"$BACKEND_DIR/lib/postgresql.jar"}
if [ ! -s "$JDBC_JAR" ]; then
    mkdir -p "$(dirname -- "$JDBC_JAR")"
    curl -fL --retry 2 \
        https://repo1.maven.org/maven2/org/postgresql/postgresql/42.7.8/postgresql-42.7.8.jar \
        -o "$JDBC_JAR"
fi
mkdir -p "$BACKEND_DIR/out"
find "$BACKEND_DIR/src" -name '*.java' -print > "$BACKEND_DIR/sources.list"
javac -Xlint:all -cp "$JDBC_JAR" -d "$BACKEND_DIR/out" @"$BACKEND_DIR/sources.list"
rm -f "$BACKEND_DIR/sources.list"
printf 'Compiled CineReserve backend into %s\n' "$BACKEND_DIR/out"
