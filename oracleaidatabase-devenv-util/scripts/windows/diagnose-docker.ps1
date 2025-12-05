# PowerShell version of diagnose-docker.sh
# Diagnose Docker configuration and permissions

$ErrorActionPreference = "Continue"

Write-Host "== Current User =="
Write-Host "$env:USERNAME @ $env:COMPUTERNAME"
whoami

Write-Host "`n== Docker Version =="
docker --version

Write-Host "`n== Docker Info =="
docker info

Write-Host "`n== Docker Compose Version =="
docker compose version

Write-Host "`n== Testing Docker Access =="
try {
    docker ps
    Write-Host "✓ Docker access is working!"
} catch {
    Write-Host "✗ Docker access failed: $_"
}

Write-Host "`n== Next Steps =="
Write-Host @"
If Docker access failed:
1. Make sure Docker Desktop is running
2. Verify you have permissions to use Docker
3. Try running PowerShell as Administrator
4. Check Docker Desktop settings for WSL2 integration (if using WSL)
5. Re-run this script to verify: .\diagnose-docker.ps1
"@
