# Voice Assistant Integration with Oracle AI Spring Boot
# This script builds and runs the application with voice assistant enabled

# Disable OCI autoconfiguration if OCI config not available
$env:SPRING_CLOUD_OCI_ENABLED = "false"

Write-Host "================================" -ForegroundColor Cyan
Write-Host "Oracle AI - Voice Assistant" -ForegroundColor Cyan
Write-Host "================================" -ForegroundColor Cyan
Write-Host ""

# Load environment variables from .env file if it exists
$envFile = Join-Path $PSScriptRoot ".env"
if (Test-Path $envFile) {
    Write-Host "Loading configuration from .env file..." -ForegroundColor Green
    Get-Content $envFile | ForEach-Object {
        if ($_ -match '^\s*([^#][^=]+)=(.*)$') {
            $key = $matches[1].Trim()
            $value = $matches[2].Trim()
            Set-Item -Path "env:$key" -Value $value
        }
    }
    Write-Host "Environment variables loaded from .env" -ForegroundColor Green
    Write-Host ""
}

# Voice Assistant Configuration (can be overridden by .env)
if (-not $env:ENABLE_VOICE_ASSISTANT) {
    $env:ENABLE_VOICE_ASSISTANT = "true"
}
if (-not $env:PORCUPINE_ACCESS_KEY) {
    $env:PORCUPINE_ACCESS_KEY = "pqnRedDJiDceXTd7FFghk3tyYLxsaVGSvtc+9qClqtj0BJvnG3p5qw=="
}
if (-not $env:KEYWORD_PATH) {
    $env:KEYWORD_PATH = "."
}
if (-not $env:ENABLE_LANGUAGE_DETECTION) {
    $env:ENABLE_LANGUAGE_DETECTION = "true"
}
if (-not $env:RESPONSE_LANGUAGE) {
    $env:RESPONSE_LANGUAGE = "both"
}

Write-Host "Configuration:" -ForegroundColor Yellow
Write-Host "  ENABLE_VOICE_ASSISTANT: $env:ENABLE_VOICE_ASSISTANT"
Write-Host "  ENABLE_LANGUAGE_DETECTION: $env:ENABLE_LANGUAGE_DETECTION"
Write-Host "  RESPONSE_LANGUAGE: $env:RESPONSE_LANGUAGE"
Write-Host "  OPENAI_MODEL: $env:OPENAI_MODEL"
Write-Host "  TTS_ENGINE: $env:TTS_ENGINE"
Write-Host "  TTS_QUALITY: $env:TTS_QUALITY"
Write-Host ""

# Check if KEYWORD_PATH is set properly
if ($env:KEYWORD_PATH -eq ".") {
    Write-Host "WARNING: KEYWORD_PATH not set. Please set KEYWORD_PATH environment variable to your wake word model file." -ForegroundColor Red
    Write-Host "Example: `$env:KEYWORD_PATH='C:\path\to\Hey-computer_en_windows_v4_0_0.ppn'" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "Press Enter to continue anyway or Ctrl+C to stop..." -ForegroundColor Yellow
    Read-Host
}

# Check if OPENAI_API_KEY is set
if (-not $env:OPENAI_API_KEY) {
    Write-Host "WARNING: OPENAI_API_KEY not set. Set this environment variable for ChatGPT integration." -ForegroundColor Red
    Write-Host ""
}

Write-Host "Building application..." -ForegroundColor Green
mvn clean package -DskipTests

if ($LASTEXITCODE -ne 0) {
    Write-Host "Build failed!" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "Starting Oracle AI with Voice Assistant..." -ForegroundColor Green
Write-Host "Listening for wake word... (Press Ctrl+C to stop)" -ForegroundColor Cyan
Write-Host ""

java -jar .\target\oracleai-0.0.1-SNAPSHOT.jar
