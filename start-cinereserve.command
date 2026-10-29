#!/bin/bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
LOG_DIR="$HOME/Library/Logs/CineReserve"
DB_PORT=26257
API_PORT=8081
WEB_PORT=5500
API_URL="http://127.0.0.1:${API_PORT}"
WEB_URL="http://127.0.0.1:${WEB_PORT}"
STORE_DIR="$HOME/cockroach-data"

mkdir -p "$LOG_DIR"

# Homebrew tools are not always on the PATH in a Finder-launched Terminal.
for brew_bin in /opt/homebrew/bin /usr/local/bin; do
    if [ -d "$brew_bin" ]; then PATH="$brew_bin:$PATH"; fi
done
export PATH

fail() {
    printf '\nCineReserve could not start: %s\n' "$1" >&2
    printf 'Logs: %s\n' "$LOG_DIR" >&2
    read -r -p 'Press Return to close this window.' _ || true
    exit 1
}

listening() {
    lsof -nP -iTCP:"$1" -sTCP:LISTEN >/dev/null 2>&1
}

wait_for() {
    local label="$1"; shift
    local attempt
    for attempt in {1..45}; do
        if "$@" >/dev/null 2>&1; then return 0; fi
        sleep 1
    done
    fail "Timed out waiting for ${label}. See the service log in ${LOG_DIR}."
}

api_healthy() {
    local health
    health="$(curl -fsS "$API_URL/api/health" 2>/dev/null)" || return 1
    [[ "$health" == *'"service":"CineReserve API"'* \
        && "$health" == *'"database":"connected"'* ]]
}

if ! /usr/libexec/java_home -v 21 >/dev/null 2>&1; then
    fail "Java 21 is not installed. Install Temurin 21, then open this launcher again."
fi
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
export PATH="$JAVA_HOME/bin:$PATH"

if ! command -v cockroach >/dev/null 2>&1; then
    fail "CockroachDB CLI was not found. Install CockroachDB and ensure its cockroach command is on PATH."
fi
COCKROACH="$(command -v cockroach)"

if ! cockroach sql --insecure --host=localhost:"$DB_PORT" --execute='SELECT 1' >/dev/null 2>&1; then
    if listening "$DB_PORT"; then
        fail "Port ${DB_PORT} is occupied, but the listener is not accepting CockroachDB SQL. Nothing was stopped."
    fi
    nohup "$COCKROACH" start-single-node --insecure \
        --listen-addr=localhost:"$DB_PORT" --http-addr=localhost:8080 \
        --store="$STORE_DIR" >"$LOG_DIR/cockroach.log" 2>&1 </dev/null &
    wait_for "CockroachDB on port ${DB_PORT}" \
        cockroach sql --insecure --host=localhost:"$DB_PORT" --execute='SELECT 1'
else
    printf 'CockroachDB is already running; reusing it.\n'
fi

cockroach sql --insecure --host=localhost:"$DB_PORT" \
    --execute='CREATE DATABASE IF NOT EXISTS cinereserve;' \
    >"$LOG_DIR/database-setup.log" 2>&1 \
    || fail "Could not ensure the cinereserve database exists. See database-setup.log."
if cockroach sql --insecure --host=localhost:"$DB_PORT" --database=cinereserve \
    --execute='SELECT count(*) FROM vw_show_availability' \
    >>"$LOG_DIR/database-setup.log" 2>&1; then
    printf 'CineReserve schema is already present; preserving its current rows.\n'
else
    # On first setup only, these repeatable scripts create missing objects and
    # add missing sample rows; they never delete existing application rows.
    cockroach sql --insecure --host=localhost:"$DB_PORT" --database=cinereserve \
        --file="$PROJECT_DIR/database/schema.sql" \
        >>"$LOG_DIR/database-setup.log" 2>&1 \
        || fail "Could not apply the repeatable database schema. See database-setup.log."
    cockroach sql --insecure --host=localhost:"$DB_PORT" --database=cinereserve \
        --file="$PROJECT_DIR/database/seed.sql" \
        >>"$LOG_DIR/database-setup.log" 2>&1 \
        || fail "Could not apply the additive sample seed. See database-setup.log."
fi

if ! api_healthy; then
    if listening "$API_PORT"; then
        fail "Port ${API_PORT} is occupied, but it is not a healthy CineReserve API. Nothing was stopped."
    fi
    nohup "$PROJECT_DIR/backend/run.sh" >"$LOG_DIR/backend.log" 2>&1 </dev/null &
    wait_for "CineReserve backend and database connection" \
        api_healthy
fi

page_html=""
if page_html="$(curl -fsS "$WEB_URL/" 2>/dev/null)" \
    && [[ "$page_html" == *'<title>CineReserve | Experience the Movies</title>'* ]]; then
    : # The expected CineReserve page is already being served.
else
    if listening "$WEB_PORT"; then
        fail "Port ${WEB_PORT} is occupied by a server that did not serve this CineReserve page. Nothing was stopped."
    fi
    nohup python3 -m http.server "$WEB_PORT" --bind 127.0.0.1 \
        --directory "$PROJECT_DIR" >"$LOG_DIR/frontend.log" 2>&1 </dev/null &
    wait_for "CineReserve frontend on port ${WEB_PORT}" \
        curl -fsS "$WEB_URL/"
    page_html="$(curl -fsS "$WEB_URL/")" \
        || fail "The frontend port responded, but the page could not be read."
    [[ "$page_html" == *'<title>CineReserve | Experience the Movies</title>'* ]] \
        || fail "The frontend port responded, but did not serve this CineReserve project."
fi

printf '\nCineReserve is ready.\nDatabase SQL: localhost:%s/cinereserve\nBackend:      %s/api/health\nWebsite:      %s\nLogs:         %s\n' \
    "$DB_PORT" "$API_URL" "$WEB_URL" "$LOG_DIR"
open "$WEB_URL"
