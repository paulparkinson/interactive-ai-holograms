# PowerShell version of rebuild-spring.sh
# Rebuilds and restarts the Spring Boot container

$ErrorActionPreference = "Stop"

# Load CONTAINER_RUNTIME from .env
$CONTAINER_RUNTIME = "docker"
if (Test-Path ".env") {
    $envLine = Get-Content ".env" | Where-Object { $_ -match "^CONTAINER_RUNTIME=" }
    if ($envLine) {
        $CONTAINER_RUNTIME = $envLine.Split('=')[1].Trim()
    }
}

Write-Host "Rebuilding and restarting spring-app..."
& $CONTAINER_RUNTIME compose build spring-app
& $CONTAINER_RUNTIME compose up -d spring-app
Write-Host "spring-app rebuilt and restarted successfully."
Write-Host "View logs with: .\logs-spring.ps1"
