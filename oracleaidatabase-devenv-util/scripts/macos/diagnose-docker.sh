#!/usr/bin/env bash
set -euo pipefail

# Load CONTAINER_RUNTIME from .env
if [ -f .env ]; then
  export $(grep CONTAINER_RUNTIME .env | xargs)
fi
CONTAINER_RUNTIME=${CONTAINER_RUNTIME:-docker}

echo "== Container Runtime: ${CONTAINER_RUNTIME} =="
echo ""

echo "== User =="
id

echo "== Groups =="
groups

if [ "${CONTAINER_RUNTIME}" = "docker" ]; then
  echo "== /var/run/docker.sock permissions =="
  ls -l /var/run/docker.sock || echo "docker.sock not found"

  echo "== docker group entry (grep docker) =="
  grep docker /etc/group || echo "No docker group line found"
else
  echo "== Podman runtime detected - no socket/group setup needed =="
  echo "Podman runs rootless by default"
fi

echo "== Environment SHELL/USER =="
echo "SHELL=$SHELL USER=$USER"

if [ "${CONTAINER_RUNTIME}" = "docker" ]; then
cat <<'EOF'
Next steps:
1. If your user is NOT listed in the docker group above -> run: sudo usermod -aG docker $USER
2. Open a NEW login session (logout/login) or run: newgrp docker (temporary subshell).
3. Re-check with: ./diagnose-docker.sh
4. Then: docker ps
EOF
else
cat <<'EOF'
Next steps for Podman:
1. Ensure Podman is installed: which podman
2. Test Podman: podman ps
3. If using rootless mode, ensure subuid/subgid are configured
EOF
fi
