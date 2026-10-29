#!/usr/bin/env sh
set -eu
BACKEND_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
"$BACKEND_DIR/build.sh"
JDBC_JAR=${CINERESERVE_JDBC_JAR:-"$BACKEND_DIR/lib/postgresql.jar"}
exec java -cp "$BACKEND_DIR/out:$JDBC_JAR" CineReserveServer
