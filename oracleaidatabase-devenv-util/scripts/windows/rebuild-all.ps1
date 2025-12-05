# PowerShell version of rebuild-all.sh
# Rebuilds and restarts both Python and Spring Boot containers

$ErrorActionPreference = "Stop"

# Load CONTAINER_RUNTIME from .env
$CONTAINER_RUNTIME = "docker"
if (Test-Path ".env") {
    $envLine = Get-Content ".env" | Where-Object { $_ -match "^CONTAINER_RUNTIME=" }
    if ($envLine) {
        $CONTAINER_RUNTIME = $envLine.Split('=')[1].Trim()
    }
}

Write-Host "Rebuilding and restarting both python-app and spring-app..."
& $CONTAINER_RUNTIME compose build python-app spring-app
& $CONTAINER_RUNTIME compose up -d python-app spring-app
Write-Host "Both applications rebuilt and restarted successfully."
Write-Host "View logs with: .\logs-python.ps1 or .\logs-spring.ps1"
