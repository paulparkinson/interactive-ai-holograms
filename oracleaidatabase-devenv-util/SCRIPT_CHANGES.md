# Script Organization and Docker/Podman Support - Changes Summary

## What Changed

### 1. Platform-Specific Folders Created

All scripts have been organized into platform-specific directories:

```
scripts/
├── linux/       # 10 bash scripts for Linux
├── macos/       # 10 bash scripts for macOS (identical to Linux)
└── windows/     # 10 PowerShell scripts for Windows
```

### 2. Docker/Podman Support Added

All scripts now support both Docker and Podman container runtimes:

- **Container Runtime Selection**: During initial setup, `start.sh`/`start.ps1` prompts for your container runtime choice (docker or podman)
- **Configuration Storage**: Your choice is saved in `.env` as `CONTAINER_RUNTIME=docker` or `CONTAINER_RUNTIME=podman`
- **Automatic Loading**: All other scripts read `CONTAINER_RUNTIME` from `.env` and use the configured runtime
- **Default Behavior**: If not specified, defaults to Docker

### 3. Scripts Updated

All 30 scripts (10 per platform × 3 platforms) have been updated:

#### Linux/macOS Scripts (`scripts/linux/` and `scripts/macos/`)
- Added `.env` loading logic to all scripts
- Changed from hardcoded `docker compose` to `${CONTAINER_RUNTIME} compose`
- Changed from `sg docker -c` to `sg ${CONTAINER_RUNTIME} -c`
- Updated `diagnose-docker.sh` to support both Docker and Podman diagnostics

#### Windows Scripts (`scripts/windows/`)
- Added PowerShell logic to read `CONTAINER_RUNTIME` from `.env`
- Changed from hardcoded `docker compose` to `& $CONTAINER_RUNTIME compose`
- All scripts now dynamically use the configured runtime

### 4. .env_example Created

Created `.env_example` with sanitized credentials for version control:
- Example passwords instead of real ones
- Placeholder API keys
- Added `CONTAINER_RUNTIME=docker` with comment
- Safe to commit to git repositories

### 5. README Updated

Updated `README.md` to reflect new structure:
- Quick Start section now references platform-specific folders
- Prerequisites mention both Docker and Podman support
- Available Scripts table updated with correct paths
- Added note about container runtime choice being saved in `.env`
- Removed references to scripts in root directory

## How to Use

### Initial Setup

**Linux:**
```bash
cd scripts/linux/
chmod +x *.sh
./start.sh
# You'll be prompted: "Container runtime (docker or podman, default: docker):"
```

**macOS:**
```bash
cd scripts/macos/
chmod +x *.sh
./start.sh
# You'll be prompted: "Container runtime (docker or podman, default: docker):"
```

**Windows:**
```powershell
cd scripts\windows\
.\start.ps1
# You'll be prompted: "Container runtime (docker or podman, default: docker):"
```

### Using Other Scripts

After initial setup, all other scripts automatically use your configured runtime:

```bash
# Linux/macOS
./teardown.sh           # Uses CONTAINER_RUNTIME from .env
./rebuild-python.sh     # Uses CONTAINER_RUNTIME from .env
./logs-spring.sh        # Uses CONTAINER_RUNTIME from .env
```

```powershell
# Windows
.\teardown.ps1          # Uses CONTAINER_RUNTIME from .env
.\rebuild-python.ps1    # Uses CONTAINER_RUNTIME from .env
.\logs-spring.ps1       # Uses CONTAINER_RUNTIME from .env
```

### Switching Container Runtimes

To switch from Docker to Podman (or vice versa):

1. **Option 1**: Edit `.env` and change `CONTAINER_RUNTIME=docker` to `CONTAINER_RUNTIME=podman`
2. **Option 2**: Delete `.env` and run `start.sh`/`start.ps1` again to reconfigure

## Technical Details

### How Scripts Load CONTAINER_RUNTIME

**Bash (Linux/macOS):**
```bash
# Load CONTAINER_RUNTIME from .env
if [ -f .env ]; then
  export $(grep CONTAINER_RUNTIME .env | xargs)
fi
CONTAINER_RUNTIME=${CONTAINER_RUNTIME:-docker}
```

**PowerShell (Windows):**
```powershell
# Load CONTAINER_RUNTIME from .env
$CONTAINER_RUNTIME = "docker"
if (Test-Path ".env") {
    $envLine = Get-Content ".env" | Where-Object { $_ -match "^CONTAINER_RUNTIME=" }
    if ($envLine) {
        $CONTAINER_RUNTIME = $envLine.Split('=')[1].Trim()
    }
}
```

### Validation

The `start.sh`/`start.ps1` scripts validate the container runtime choice:

**Bash:**
```bash
if [[ "$CONTAINER_RUNTIME" != "docker" && "$CONTAINER_RUNTIME" != "podman" ]]; then
  echo "Error: CONTAINER_RUNTIME must be 'docker' or 'podman'"
  exit 1
fi
```

**PowerShell:**
```powershell
if ($CONTAINER_RUNTIME -ne "docker" -and $CONTAINER_RUNTIME -ne "podman") {
    Write-Error "Error: CONTAINER_RUNTIME must be 'docker' or 'podman'"
    exit 1
}
```

## Complete Script List

All scripts in each platform folder:

1. **start.sh / start.ps1** - Interactive setup and start all services
2. **teardown.sh / teardown.ps1** - Stop all services and remove volumes
3. **rebuild-python.sh / rebuild-python.ps1** - Rebuild and restart Python app only
4. **rebuild-spring.sh / rebuild-spring.ps1** - Rebuild and restart Spring app only
5. **rebuild-all.sh / rebuild-all.ps1** - Rebuild and restart both apps
6. **logs-python.sh / logs-python.ps1** - View Python app logs
7. **logs-spring.sh / logs-spring.ps1** - View Spring app logs
8. **logs-oracle.sh / logs-oracle.ps1** - View Oracle DB logs
9. **diagnose-docker.sh / diagnose-docker.ps1** - Diagnose container runtime configuration
10. **docker.sh / docker.ps1** - Wrapper for container commands

## Benefits

1. **Flexibility**: Choose between Docker and Podman based on your environment
2. **Organization**: Clear separation between Linux, macOS, and Windows scripts
3. **Consistency**: All scripts use the same container runtime throughout
4. **Portability**: Take the repo to any machine, choose your runtime, and start
5. **Security**: `.env_example` provides safe template for version control

## Podman Notes

If using Podman:
- Ensure `podman-compose` is installed (or Podman 4.0+ with native compose support)
- Rootless mode is supported
- On Linux, you may need to configure subuid/subgid mappings
- Run `./diagnose-docker.sh` (which now supports Podman) to verify configuration
