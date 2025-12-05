# PowerShell version of teardown.sh
# Tears down compose services, removing volumes and orphan containers

$ErrorActionPreference = "Stop"

# Load CONTAINER_RUNTIME from .env
$CONTAINER_RUNTIME = "docker"
if (Test-Path ".env") {
    $envLine = Get-Content ".env" | Where-Object { $_ -match "^CONTAINER_RUNTIME=" }
    if ($envLine) {
        $CONTAINER_RUNTIME = $envLine.Split('=')[1].Trim()
    }
}

Write-Host "Tearing down $CONTAINER_RUNTIME compose services, removing volumes and orphan containers..."
& $CONTAINER_RUNTIME compose down -v --remove-orphans

Write-Host "Teardown complete."
