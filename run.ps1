# Load environment variables from .env and run the packaged AI Holo JAR.

$ErrorActionPreference = "Stop"

$projectRoot = $PSScriptRoot
Set-Location $projectRoot

$envFilePath = Join-Path $projectRoot ".env"
$jarPath = Join-Path $projectRoot "target\aiholo.jar"

if (-not (Test-Path $envFilePath)) {
    Write-Host "ERROR: .env file not found at $envFilePath" -ForegroundColor Red
    exit 1
}

if (-not (Test-Path $jarPath)) {
    Write-Host "ERROR: JAR not found at $jarPath" -ForegroundColor Red
    Write-Host "Build it first with .\build.ps1" -ForegroundColor Yellow
    exit 1
}

Get-Content $envFilePath | ForEach-Object {
    $line = $_.Trim()
    if ($line -and -not $line.StartsWith("#")) {
        $parts = $line -split '=', 2
        if ($parts.Count -eq 2) {
            $key = $parts[0].Trim()
            $value = $parts[1].Trim()
            [Environment]::SetEnvironmentVariable($key, $value, "Process")
        }
    }
}

Write-Host "Running AI Holo..." -ForegroundColor Cyan
Write-Host "JAR: $jarPath" -ForegroundColor Green

& java -jar $jarPath
