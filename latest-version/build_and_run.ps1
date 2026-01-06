# Voice Assistant Integration with Oracle AI Spring Boot
# This script builds and runs the application with voice assistant enabled

$ErrorActionPreference = "Stop"

# ========== Load Environment Variables from .env file ==========
Write-Host "Loading environment variables from .env file..." -ForegroundColor Cyan

$envFilePath = Join-Path $PSScriptRoot ".env"

if (Test-Path $envFilePath) {
    Get-Content $envFilePath | ForEach-Object {
        $line = $_.Trim()
        # Skip empty lines and comments
        if ($line -and !$line.StartsWith("#")) {
            # Split on first = sign
            $parts = $line -split '=', 2
            if ($parts.Count -eq 2) {
                $key = $parts[0].Trim()
                $value = $parts[1].Trim()
                # Set environment variable
                [Environment]::SetEnvironmentVariable($key, $value, "Process")
            }
        }
    }
    Write-Host "Environment variables loaded successfully.`n" -ForegroundColor Green
} else {
    Write-Host "Warning: .env file not found at $envFilePath" -ForegroundColor Yellow
    Write-Host "Using default/existing environment variables...`n" -ForegroundColor Yellow
}

Write-Host "================================"
Write-Host "Oracle AI - Voice Assistant"
Write-Host "================================"
Write-Host ""

# Configuration

Write-Host "Configuration:"
Write-Host "  ENABLE_VOICE_ASSISTANT: $env:ENABLE_VOICE_ASSISTANT"
Write-Host "  ENABLE_LANGUAGE_DETECTION: $env:ENABLE_LANGUAGE_DETECTION"
Write-Host "  RESPONSE_LANGUAGE: $env:RESPONSE_LANGUAGE"
Write-Host "  TTS_ENGINE: $env:TTS_ENGINE ($env:TTS_QUALITY)"
Write-Host "  AUDIO_DEVICE_A: $env:AUDIO_DEVICE_A"
Write-Host "  AUDIO_DEVICE_B: $env:AUDIO_DEVICE_B"
Write-Host "  KEYWORD_PATH: $env:KEYWORD_PATH"
Write-Host ""

# Check if KEYWORD_PATH exists
if (-not (Test-Path $env:KEYWORD_PATH)) {
    Write-Host "ERROR: Keyword file not found at: $env:KEYWORD_PATH"
    Write-Host "Please set KEYWORD_PATH environment variable to your wake word model file."
    exit 1
}

# Check if OPENAI_API_KEY is set
if (-not $env:OPENAI_API_KEY) {
    Write-Host "WARNING: OPENAI_API_KEY not set. Set this environment variable for ChatGPT integration."
}

Write-Host "Building application..."
mvn clean package -DskipTests -q

if ($LASTEXITCODE -ne 0) {
    Write-Host "ERROR: Build failed!"
    exit 1
}

Write-Host ""
Write-Host "Starting Oracle AI with Voice Assistant..."
Write-Host "Listening for wake word... (Press Ctrl+C to stop)"
Write-Host ""

mvn spring-boot:run
# java -jar target/oracleai-0.0.1-SNAPSHOT.jar
