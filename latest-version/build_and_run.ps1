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

# ========== Auto-detect Java and Maven ==========
Write-Host "Checking Java and Maven..." -ForegroundColor Cyan

# Auto-detect Java if JAVA_HOME not set
if (-not $env:JAVA_HOME) {
    Write-Host "JAVA_HOME not set. Attempting to auto-detect Java..." -ForegroundColor Yellow
    
    # Try to find java executable in PATH
    $javaCmd = Get-Command java -ErrorAction SilentlyContinue
    if ($javaCmd) {
        # Get java path and derive JAVA_HOME
        $javaPath = $javaCmd.Source
        $javaBinDir = Split-Path -Parent $javaPath
        $javaHomeCandidate = Split-Path -Parent $javaBinDir
        
        # Verify this looks like a valid JAVA_HOME (has bin\java.exe and bin\javac.exe)
        if ((Test-Path "$javaHomeCandidate\bin\java.exe") -and (Test-Path "$javaHomeCandidate\bin\javac.exe")) {
            $env:JAVA_HOME = $javaHomeCandidate
            Write-Host "Auto-detected JAVA_HOME from PATH: $env:JAVA_HOME" -ForegroundColor Green
        }
    }
    
    # Still not found? Try common installation paths
    if (-not $env:JAVA_HOME) {
        $commonPaths = @(
            "C:\Program Files\GraalVM\*",
            "C:\Program Files\Java\jdk*",
            "C:\Program Files\Eclipse Adoptium\jdk*",
            "C:\Program Files\Microsoft\jdk*",
            "C:\Program Files\Amazon Corretto\jdk*",
            "C:\Program Files\BellSoft\*jdk*"
        )
        
        foreach ($pattern in $commonPaths) {
            $jdkDirs = Get-ChildItem -Path $pattern -Directory -ErrorAction SilentlyContinue | 
                       Where-Object { Test-Path "$($_.FullName)\bin\java.exe" -and Test-Path "$($_.FullName)\bin\javac.exe" } |
                       Sort-Object Name -Descending
            if ($jdkDirs) {
                $env:JAVA_HOME = $jdkDirs[0].FullName
                Write-Host "Auto-detected JAVA_HOME: $env:JAVA_HOME" -ForegroundColor Green
                break
            }
        }
    }
    
    # Still not found? Error out with helpful message
    if (-not $env:JAVA_HOME) {
        Write-Host "ERROR: Cannot find Java installation!" -ForegroundColor Red
        Write-Host ""
        Write-Host "Please install Java 17 or later, or set JAVA_HOME manually:" -ForegroundColor Yellow
        Write-Host "  Download: https://adoptium.net/" -ForegroundColor Cyan
        Write-Host "  Or set: " -NoNewline -ForegroundColor Yellow
        Write-Host "`$env:JAVA_HOME = 'C:\Path\To\Java\jdk'" -ForegroundColor White
        exit 1
    }
} else {
    # Validate existing JAVA_HOME
    if (-not ((Test-Path "$env:JAVA_HOME\bin\java.exe") -and (Test-Path "$env:JAVA_HOME\bin\javac.exe"))) {
        Write-Host "WARNING: JAVA_HOME is set but appears invalid: $env:JAVA_HOME" -ForegroundColor Yellow
        Write-Host "Attempting to find valid Java installation..." -ForegroundColor Yellow
        
        # Try to find from PATH
        $javaCmd = Get-Command java -ErrorAction SilentlyContinue
        if ($javaCmd) {
            $javaPath = $javaCmd.Source
            $javaBinDir = Split-Path -Parent $javaPath
            $javaHomeCandidate = Split-Path -Parent $javaBinDir
            
            if ((Test-Path "$javaHomeCandidate\bin\java.exe") -and (Test-Path "$javaHomeCandidate\bin\javac.exe")) {
                $env:JAVA_HOME = $javaHomeCandidate
                Write-Host "Corrected JAVA_HOME: $env:JAVA_HOME" -ForegroundColor Green
            }
        }
    }
}

# Verify Maven is available
$mvnCmd = Get-Command mvn -ErrorAction SilentlyContinue
if (-not $mvnCmd) {
    Write-Host "ERROR: Maven (mvn) not found in PATH!" -ForegroundColor Red
    Write-Host ""
    Write-Host "Please install Maven or add it to PATH:" -ForegroundColor Yellow
    Write-Host "  Download: https://maven.apache.org/download.cgi" -ForegroundColor Cyan
    Write-Host "  Or add to PATH: " -NoNewline -ForegroundColor Yellow
    Write-Host "`$env:PATH += ';C:\Path\To\Maven\bin'" -ForegroundColor White
    exit 1
}

Write-Host "Java: $env:JAVA_HOME" -ForegroundColor Green
Write-Host "Maven: $($mvnCmd.Source)" -ForegroundColor Green
Write-Host ""

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
