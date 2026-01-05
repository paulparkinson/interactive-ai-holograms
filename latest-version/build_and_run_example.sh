#!/bin/bash

# Oracle AI Application - Build and Run Script
# Supports both standard mode and voice assistant mode
# Usage: ./build_and_run.sh [--voice]
#   (no args)  = Run standard application
#   --voice    = Run with voice assistant enabled

set -e

# Disable OCI autoconfiguration if OCI config not available
export SPRING_CLOUD_OCI_ENABLED=false

echo "================================"
echo "Oracle AI - Build and Run"
echo "================================"
echo ""

# Check if voice assistant mode is requested
VOICE_MODE="false"
if [ "$1" = "--voice" ] || [ "$1" = "-v" ]; then
    VOICE_MODE="true"
fi

# Set Java and Maven paths for Linux/macOS
if command -v java &> /dev/null; then
    JAVA_BIN=$(command -v java)
else
    export JAVA_HOME="$(/usr/libexec/java_home -v 11 2>/dev/null || echo /usr/lib/jvm/java-11-openjdk)"
fi

export MAVEN_HOME="${MAVEN_HOME:-/usr/local/opt/maven}"
export PATH="$MAVEN_HOME/bin:${JAVA_HOME}/bin:$PATH"

# ============================================================================
# STANDARD APPLICATION CONFIGURATION
# ============================================================================

export SANDBOX_API_URL="${SANDBOX_API_URL:-http://YOUR_SANDBOX_SERVER/v1/chat/completions?client=server}"
export AI_OPTIMZER="${AI_OPTIMZER:-Bearer YOUR_AI_OPTIMIZER_TOKEN_HERE}"
export OPENAI_API_KEY="${OPENAI_API_KEY:-sk-YOUR_OPENAI_API_KEY_HERE}"
export OPENAI_MODEL="${OPENAI_MODEL:-gpt-4}"
export OCI_VISION_ENDPOINT="${OCI_VISION_ENDPOINT:-https://vision.aiservice.YOUR_REGION.oci.oraclecloud.com/20220125}"
export OCI_COMPARTMENT_ID="${OCI_COMPARTMENT_ID:-ocid1.compartment.oc1..YOUR_COMPARTMENT_ID}"
export AIHOLO_HOST_URL="${AIHOLO_HOST_URL:-http://localhost:8080}"
export AUDIO_DIR_PATH="${AUDIO_DIR_PATH:-./src/main/resources/static/audio-aiholo/}"
export SERVER_PORT="${SERVER_PORT:-8080}"

# ============================================================================
# VOICE ASSISTANT CONFIGURATION (Optional)
# ============================================================================

if [ "$VOICE_MODE" = "true" ]; then
    echo "MODE: Voice Assistant Enabled"
    echo ""
    
    export ENABLE_VOICE_ASSISTANT="true"
    export PORCUPINE_ACCESS_KEY="${PORCUPINE_ACCESS_KEY:-pqnRedDJiDceXTd7FFghk3tyYLxsaVGSvtc+9qClqtj0BJvnG3p5qw==}"
    export KEYWORD_PATH="${KEYWORD_PATH:-.}"
    export ENABLE_LANGUAGE_DETECTION="${ENABLE_LANGUAGE_DETECTION:-true}"
    export RESPONSE_LANGUAGE="${RESPONSE_LANGUAGE:-both}"
    
    echo "Voice Assistant Configuration:"
    echo "  ENABLE_VOICE_ASSISTANT: $ENABLE_VOICE_ASSISTANT"
    echo "  ENABLE_LANGUAGE_DETECTION: $ENABLE_LANGUAGE_DETECTION"
    echo "  RESPONSE_LANGUAGE: $RESPONSE_LANGUAGE"
    echo ""
    
    # Check if KEYWORD_PATH is set properly
    if [ "$KEYWORD_PATH" = "." ]; then
        echo "ERROR: KEYWORD_PATH not set"
        echo "Please set KEYWORD_PATH environment variable to your wake word model file."
        echo "Example: export KEYWORD_PATH='/path/to/Hey-computer_en_linux_v3_0_0.ppn'"
        exit 1
    fi
    
    # Check if KEYWORD_PATH file exists
    if [ ! -f "$KEYWORD_PATH" ]; then
        echo "ERROR: Keyword file not found at: $KEYWORD_PATH"
        exit 1
    fi
    
    # Warn if OPENAI_API_KEY is not set
    if [ -z "$OPENAI_API_KEY" ] || [ "$OPENAI_API_KEY" = "sk-YOUR_OPENAI_API_KEY_HERE" ]; then
        echo "WARNING: OPENAI_API_KEY not properly configured. ChatGPT integration will not work."
    fi
else
    echo "MODE: Standard Application"
    echo ""
fi

# Display selected configuration
echo "General Configuration:"
echo "  SERVER_PORT: $SERVER_PORT"
echo "  AIHOLO_HOST_URL: $AIHOLO_HOST_URL"
echo ""

# Build the application
echo "Building application..."
mvn clean package -DskipTests -q

if [ $? -ne 0 ]; then
    echo "ERROR: Build failed!"
    exit 1
fi

echo ""
echo "Build successful!"
echo ""

# Run the application
if [ "$VOICE_MODE" = "true" ]; then
    echo "Starting Oracle AI with Voice Assistant..."
    echo "Listening for wake word... (Press Ctrl+C to stop)"
else
    echo "Starting Oracle AI..."
fi

echo ""
mvn spring-boot:run