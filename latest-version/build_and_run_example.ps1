# Oracle AI Application - Build and Run Script
# Supports both standard mode and voice assistant mode
# Usage: .\build_and_run.ps1 [-Voice]
#   (no args)  = Run standard application
#   -Voice     = Run with voice assistant enabled

param(
    [switch]$Voice = $false
)

$ErrorActionPreference = "Stop"

# Disable OCI autoconfiguration if OCI config not available
$env:SPRING_CLOUD_OCI_ENABLED = "false"

Write-Host "================================"
Write-Host "Oracle AI - Build and Run"
Write-Host "================================"
Write-Host ""

# Disable OCI autoconfiguration if OCI config not available
$env:spring_cloud_oci_enabled = "false"

# Set Java and Maven paths
$env:JAVA_HOME = "C:\Program Files\BellSoft\LibericaNIK-23-OpenJDK-21"
$env:MAVEN_HOME = "C:\tools\apache-maven-3.9.6"
$env:PATH = "$env:MAVEN_HOME\bin;$env:JAVA_HOME\bin;$env:PATH"

# ============================================================================
# STANDARD APPLICATION CONFIGURATION
# ============================================================================

$env:SANDBOX_API_URL = "http://YOUR_SANDBOX_SERVER/v1/chat/completions?client=server"
$env:AI_OPTIMZER = "Bearer YOUR_AI_OPTIMIZER_TOKEN_HERE"
$env:OPENAI_API_KEY = if ($env:OPENAI_API_KEY) { $env:OPENAI_API_KEY } else { "sk-YOUR_OPENAI_API_KEY_HERE" }
$env:OPENAI_MODEL = "gpt-4"
$env:OCI_VISION_ENDPOINT = "https://vision.aiservice.YOUR_REGION.oci.oraclecloud.com/20220125"
$env:OCI_COMPARTMENT_ID = "ocid1.compartment.oc1..YOUR_COMPARTMENT_ID"
$env:AIHOLO_HOST_URL = "http://localhost:8080"
$env:AUDIO_DIR_PATH = "C:/path/to/your/project/src/main/resources/static/audio-aiholo/"
$env:SERVER_PORT = "8080"

# ============================================================================
# VOICE ASSISTANT CONFIGURATION (Optional)
# ============================================================================

if ($Voice) {
    Write-Host "MODE: Voice Assistant Enabled"
    Write-Host ""
    
    $env:ENABLE_VOICE_ASSISTANT = "true"
    $env:PORCUPINE_ACCESS_KEY = if ($env:PORCUPINE_ACCESS_KEY) { $env:PORCUPINE_ACCESS_KEY } else { "pqnRedDJiDceXTd7FFghk3tyYLxsaVGSvtc+9qClqtj0BJvnG3p5qw==" }
    $env:KEYWORD_PATH = if ($env:KEYWORD_PATH) { $env:KEYWORD_PATH } else { "C:\Users\Ruirui\Downloads\Hey-computer_en_windows_v4_0_0\Hey-computer_en_windows_v4_0_0.ppn" }
    $env:ENABLE_LANGUAGE_DETECTION = "true"
    $env:RESPONSE_LANGUAGE = "both"
    
    Write-Host "Voice Assistant Configuration:"
    Write-Host "  ENABLE_VOICE_ASSISTANT: $env:ENABLE_VOICE_ASSISTANT"
    Write-Host "  ENABLE_LANGUAGE_DETECTION: $env:ENABLE_LANGUAGE_DETECTION"
    Write-Host "  RESPONSE_LANGUAGE: $env:RESPONSE_LANGUAGE"
    Write-Host "  KEYWORD_PATH: $env:KEYWORD_PATH"
    Write-Host ""
    
    # Check if KEYWORD_PATH exists
    if (-not (Test-Path $env:KEYWORD_PATH)) {
        Write-Host "ERROR: Keyword file not found at: $env:KEYWORD_PATH"
        Write-Host "Please set KEYWORD_PATH environment variable to your wake word model file."
        exit 1
    }
    
    # Warn if OPENAI_API_KEY is not set
    if (-not $env:OPENAI_API_KEY -or $env:OPENAI_API_KEY -eq "sk-YOUR_OPENAI_API_KEY_HERE") {
        Write-Host "WARNING: OPENAI_API_KEY not properly configured. ChatGPT integration will not work."
    }
} else {
    Write-Host "MODE: Standard Application"
    Write-Host ""
}

# Display selected configuration
Write-Host "General Configuration:"
Write-Host "  SERVER_PORT: $env:SERVER_PORT"
Write-Host "  AIHOLO_HOST_URL: $env:AIHOLO_HOST_URL"
Write-Host ""

# Build the application
Write-Host "Building application..."
mvn clean package -DskipTests -q

if ($LASTEXITCODE -ne 0) {
    Write-Host "ERROR: Build failed!"
    exit 1
}

Write-Host ""
Write-Host "Build successful!"
Write-Host ""

# Run the application
if ($Voice) {
    Write-Host "Starting Oracle AI with Voice Assistant..."
    Write-Host "Listening for wake word... (Press Ctrl+C to stop)"
} else {
    Write-Host "Starting Oracle AI..."
}

Write-Host ""
mvn spring-boot:run