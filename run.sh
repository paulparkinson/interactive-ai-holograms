#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

ENV_FILE="$SCRIPT_DIR/.env"
JAR_PATH="$SCRIPT_DIR/target/aiholo.jar"

if [ ! -f "$ENV_FILE" ]; then
    echo "ERROR: .env file not found at $ENV_FILE" >&2
    exit 1
fi

if [ ! -f "$JAR_PATH" ]; then
    echo "ERROR: JAR not found at $JAR_PATH" >&2
    echo "Build it first with mvn clean package -DskipTests" >&2
    exit 1
fi

set -a
. "$ENV_FILE"
set +a

echo "Running AI Holo..."
echo "JAR: $JAR_PATH"

exec java -jar "$JAR_PATH"
