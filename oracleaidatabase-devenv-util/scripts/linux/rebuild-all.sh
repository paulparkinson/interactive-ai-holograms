#!/usr/bin/env bash
set -euo pipefail

# Load CONTAINER_RUNTIME from .env
if [ -f .env ]; then
  export $(grep CONTAINER_RUNTIME .env | xargs)
fi
CONTAINER_RUNTIME=${CONTAINER_RUNTIME:-docker}

echo "Rebuilding and restarting both python-app and spring-app..."
sg ${CONTAINER_RUNTIME} -c "${CONTAINER_RUNTIME} compose build python-app spring-app && ${CONTAINER_RUNTIME} compose up -d python-app spring-app"
echo "Both applications rebuilt and restarted successfully."
echo "View logs with: ./logs-python.sh or ./logs-spring.sh"
