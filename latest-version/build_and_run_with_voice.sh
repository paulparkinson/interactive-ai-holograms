#!/bin/bash

# Voice Assistant Integration with Oracle AI Spring Boot
# This script builds and runs the application with voice assistant enabled

set -e

# Disable OCI autoconfiguration if OCI config not available
export SPRING_CLOUD_OCI_ENABLED=false

echo "================================"
echo "Oracle AI - Voice Assistant"
echo "================================"
echo ""

# Set Java and Maven paths for macOS
export JAVA_HOME="$(/usr/libexec/java_home -v 11)"
export MAVEN_HOME="/usr/local/opt/maven"
export PATH="$MAVEN_HOME/bin:$JAVA_HOME/bin:$PATH"

# Configuration
export ENABLE_VOICE_ASSISTANT="true"
export PORCUPINE_ACCESS_KEY="${PORCUPINE_ACCESS_KEY:-pqnRedDJiDceXTd7FFghk3tyYLxsaVGSvtc+9qClqtj0BJvnG3p5qw==}"
export KEYWORD_PATH="${KEYWORD_PATH:-.}"  # Update this to your keyword file path
export ENABLE_LANGUAGE_DETECTION="true"
export RESPONSE_LANGUAGE="both"
# OPENAI_API_KEY should be set in environment

echo "Configuration:"
echo "  ENABLE_VOICE_ASSISTANT: $ENABLE_VOICE_ASSISTANT"
echo "  ENABLE_LANGUAGE_DETECTION: $ENABLE_LANGUAGE_DETECTION"
echo "  RESPONSE_LANGUAGE: $RESPONSE_LANGUAGE"
echo ""

# Check if KEYWORD_PATH is set properly
if [ "$KEYWORD_PATH" = "." ]; then
    echo "WARNING: KEYWORD_PATH not set. Please set KEYWORD_PATH environment variable to your wake word model file."
    echo "Example: export KEYWORD_PATH='/path/to/Hey-computer_en_linux_v3_0_0.ppn'"
    exit 1
fi

# Check if OPENAI_API_KEY is set
if [ -z "$OPENAI_API_KEY" ]; then
    echo "WARNING: OPENAI_API_KEY not set. Set this environment variable for ChatGPT integration."
fi

echo "Building application..."
mvn clean package -DskipTests -q

echo ""
echo "Starting Oracle AI with Voice Assistant..."
echo "Listening for wake word... (Press Ctrl+C to stop)"
echo ""

mvn spring-boot:run
