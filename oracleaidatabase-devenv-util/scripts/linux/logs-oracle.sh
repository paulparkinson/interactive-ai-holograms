#!/usr/bin/env bash
set -euo pipefail

# Load CONTAINER_RUNTIME from .env
if [ -f .env ]; then
  export $(grep CONTAINER_RUNTIME .env | xargs)
fi
CONTAINER_RUNTIME=${CONTAINER_RUNTIME:-docker}

# Stream logs for the Oracle DB container
# Service name in docker-compose.yml is 'oracle'
exec sg ${CONTAINER_RUNTIME} -c "${CONTAINER_RUNTIME} compose logs -f --tail=200 oracle"
