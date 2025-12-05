#!/usr/bin/env bash
set -euo pipefail

# Load CONTAINER_RUNTIME from .env
if [ -f .env ]; then
  export $(grep CONTAINER_RUNTIME .env | xargs)
fi
CONTAINER_RUNTIME=${CONTAINER_RUNTIME:-docker}

echo "Tearing down ${CONTAINER_RUNTIME} compose services, removing volumes and orphan containers..."
sg ${CONTAINER_RUNTIME} -c "${CONTAINER_RUNTIME} compose down -v --remove-orphans"

echo "Teardown complete." 
