#!/usr/bin/env bash
set -euo pipefail

# Load CONTAINER_RUNTIME from .env
if [ -f .env ]; then
  export $(grep CONTAINER_RUNTIME .env | xargs)
fi
CONTAINER_RUNTIME=${CONTAINER_RUNTIME:-docker}

echo "Rebuilding and restarting spring-app..."
sg ${CONTAINER_RUNTIME} -c "${CONTAINER_RUNTIME} compose build spring-app && ${CONTAINER_RUNTIME} compose up -d spring-app"
echo "spring-app rebuilt and restarted successfully."
echo "View logs with: ./logs-spring.sh"
