# PowerShell version of logs-oracle.sh
# Stream logs for the Oracle DB container

$ErrorActionPreference = "Stop"

# Load CONTAINER_RUNTIME from .env
$CONTAINER_RUNTIME = "docker"
if (Test-Path ".env") {
    $envLine = Get-Content ".env" | Where-Object { $_ -match "^CONTAINER_RUNTIME=" }
    if ($envLine) {
        $CONTAINER_RUNTIME = $envLine.Split('=')[1].Trim()
    }
}

& $CONTAINER_RUNTIME compose logs -f --tail=200 oracle
