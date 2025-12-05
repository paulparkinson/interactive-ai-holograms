# PowerShell version of rebuild-python.sh
# Rebuilds and restarts the Python FastAPI container

$ErrorActionPreference = "Stop"

# Load CONTAINER_RUNTIME from .env
$CONTAINER_RUNTIME = "docker"
if (Test-Path ".env") {
    $envLine = Get-Content ".env" | Where-Object { $_ -match "^CONTAINER_RUNTIME=" }
    if ($envLine) {
        $CONTAINER_RUNTIME = $envLine.Split('=')[1].Trim()
    }
}

Write-Host "Rebuilding and restarting python-app..."
& $CONTAINER_RUNTIME compose build python-app
& $CONTAINER_RUNTIME compose up -d python-app
Write-Host "python-app rebuilt and restarted successfully."
Write-Host "View logs with: .\logs-python.ps1"
