#!/bin/bash
# Voice Assistant Integration with Oracle AI Spring Boot
# This script builds and runs the application with voice assistant enabled

set -e

# ========== Load Environment Variables from .env file ==========
echo -e "\033[36mLoading environment variables from .env file...\033[0m"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="$SCRIPT_DIR/.env"

if [ -f "$ENV_FILE" ]; then
    # Load environment variables from .env file
    while IFS= read -r line || [ -n "$line" ]; do
        # Trim whitespace
        line=$(echo "$line" | xargs)
        # Skip empty lines and comments
        if [ -n "$line" ] && [[ ! "$line" =~ ^# ]]; then
            # Export the variable
            export "$line"
        fi
    done < "$ENV_FILE"
    echo -e "\033[32mEnvironment variables loaded successfully.\n\033[0m"
else
    echo -e "\033[33mWarning: .env file not found at $ENV_FILE\033[0m"
    echo -e "\033[33mUsing default/existing environment variables...\n\033[0m"
fi

echo "================================"
echo "Oracle AI - Voice Assistant"
echo "================================"
echo ""

# Configuration
echo "Configuration:"
echo "  ENABLE_VOICE_ASSISTANT: $ENABLE_VOICE_ASSISTANT"
echo "  ENABLE_LANGUAGE_DETECTION: $ENABLE_LANGUAGE_DETECTION"
echo "  RESPONSE_LANGUAGE: $RESPONSE_LANGUAGE"
echo "  TTS_ENGINE: $TTS_ENGINE ($TTS_QUALITY)"
echo "  AUDIO_DEVICE_A: $AUDIO_DEVICE_A"
echo "  AUDIO_DEVICE_B: $AUDIO_DEVICE_B"
echo "  KEYWORD_PATH: $KEYWORD_PATH"
echo ""

# Check if KEYWORD_PATH exists
if [ ! -f "$KEYWORD_PATH" ]; then
    echo "ERROR: Keyword file not found at: $KEYWORD_PATH"
    echo "Please set KEYWORD_PATH environment variable to your wake word model file."
    exit 1
fi

# Check if OPENAI_API_KEY is set
if [ -z "$OPENAI_API_KEY" ]; then
    echo "WARNING: OPENAI_API_KEY not set. Set this environment variable for ChatGPT integration."
fi

# ========== Auto-detect Java and Maven ==========
echo -e "\033[36mChecking Java and Maven...\033[0m"

# Auto-detect Java if JAVA_HOME not set
if [ -z "$JAVA_HOME" ]; then
    echo -e "\033[33mJAVA_HOME not set. Attempting to auto-detect Java...\033[0m"
    
    # Try to find java executable in PATH
    if command -v java &> /dev/null; then
        JAVA_PATH=$(command -v java)
        # Resolve symlinks
        JAVA_PATH=$(readlink -f "$JAVA_PATH" 2>/dev/null || realpath "$JAVA_PATH" 2>/dev/null || echo "$JAVA_PATH")
        JAVA_BIN_DIR=$(dirname "$JAVA_PATH")
        JAVA_HOME_CANDIDATE=$(dirname "$JAVA_BIN_DIR")
        
        # Verify this looks like a valid JAVA_HOME (has bin/java and bin/javac)
        if [ -f "$JAVA_HOME_CANDIDATE/bin/java" ] && [ -f "$JAVA_HOME_CANDIDATE/bin/javac" ]; then
            export JAVA_HOME="$JAVA_HOME_CANDIDATE"
            echo -e "\033[32mAuto-detected JAVA_HOME from PATH: $JAVA_HOME\033[0m"
        fi
    fi
    
    # Still not found? Try common installation paths on macOS
    if [ -z "$JAVA_HOME" ]; then
        # macOS specific paths
        if [ -d "/Library/Java/JavaVirtualMachines" ]; then
            # Find the latest JDK
            LATEST_JDK=$(ls -1 /Library/Java/JavaVirtualMachines | grep -E 'jdk|graalvm' | sort -r | head -n 1)
            if [ -n "$LATEST_JDK" ]; then
                JAVA_HOME_CANDIDATE="/Library/Java/JavaVirtualMachines/$LATEST_JDK/Contents/Home"
                if [ -f "$JAVA_HOME_CANDIDATE/bin/java" ] && [ -f "$JAVA_HOME_CANDIDATE/bin/javac" ]; then
                    export JAVA_HOME="$JAVA_HOME_CANDIDATE"
                    echo -e "\033[32mAuto-detected JAVA_HOME: $JAVA_HOME\033[0m"
                fi
            fi
        fi
        
        # Try /usr/libexec/java_home on macOS
        if [ -z "$JAVA_HOME" ] && command -v /usr/libexec/java_home &> /dev/null; then
            JAVA_HOME_CANDIDATE=$(/usr/libexec/java_home 2>/dev/null)
            if [ $? -eq 0 ] && [ -n "$JAVA_HOME_CANDIDATE" ]; then
                export JAVA_HOME="$JAVA_HOME_CANDIDATE"
                echo -e "\033[32mAuto-detected JAVA_HOME using java_home: $JAVA_HOME\033[0m"
            fi
        fi
        
        # Linux paths
        if [ -z "$JAVA_HOME" ] && [ -d "/usr/lib/jvm" ]; then
            LATEST_JDK=$(ls -1 /usr/lib/jvm | grep -E 'java-[0-9]+-openjdk|jdk' | sort -r | head -n 1)
            if [ -n "$LATEST_JDK" ]; then
                JAVA_HOME_CANDIDATE="/usr/lib/jvm/$LATEST_JDK"
                if [ -f "$JAVA_HOME_CANDIDATE/bin/java" ] && [ -f "$JAVA_HOME_CANDIDATE/bin/javac" ]; then
                    export JAVA_HOME="$JAVA_HOME_CANDIDATE"
                    echo -e "\033[32mAuto-detected JAVA_HOME: $JAVA_HOME\033[0m"
                fi
            fi
        fi
    fi
    
    # Still not found? Error out with helpful message
    if [ -z "$JAVA_HOME" ]; then
        echo -e "\033[31mERROR: Cannot find Java installation!\033[0m"
        echo ""
        echo -e "\033[33mPlease install Java 17 or later, or set JAVA_HOME manually:\033[0m"
        echo -e "\033[36m  Download: https://adoptium.net/\033[0m"
        echo -e "\033[33m  Or set: \033[0m\033[37mexport JAVA_HOME='/path/to/java/jdk'\033[0m"
        exit 1
    fi
else
    # Validate existing JAVA_HOME
    if [ ! -f "$JAVA_HOME/bin/java" ] || [ ! -f "$JAVA_HOME/bin/javac" ]; then
        echo -e "\033[33mWARNING: JAVA_HOME is set but appears invalid: $JAVA_HOME\033[0m"
        echo -e "\033[33mAttempting to find valid Java installation...\033[0m"
        
        # Try to find from PATH
        if command -v java &> /dev/null; then
            JAVA_PATH=$(command -v java)
            JAVA_PATH=$(readlink -f "$JAVA_PATH" 2>/dev/null || realpath "$JAVA_PATH" 2>/dev/null || echo "$JAVA_PATH")
            JAVA_BIN_DIR=$(dirname "$JAVA_PATH")
            JAVA_HOME_CANDIDATE=$(dirname "$JAVA_BIN_DIR")
            
            if [ -f "$JAVA_HOME_CANDIDATE/bin/java" ] && [ -f "$JAVA_HOME_CANDIDATE/bin/javac" ]; then
                export JAVA_HOME="$JAVA_HOME_CANDIDATE"
                echo -e "\033[32mCorrected JAVA_HOME: $JAVA_HOME\033[0m"
            fi
        fi
    fi
fi

# Verify Maven is available
if ! command -v mvn &> /dev/null; then
    echo -e "\033[31mERROR: Maven (mvn) not found in PATH!\033[0m"
    echo ""
    echo -e "\033[33mPlease install Maven or add it to PATH:\033[0m"
    echo -e "\033[36m  Download: https://maven.apache.org/download.cgi\033[0m"
    echo -e "\033[33m  Or install via Homebrew (macOS): \033[0m\033[37mbrew install maven\033[0m"
    echo -e "\033[33m  Or install via package manager (Linux): \033[0m\033[37msudo apt install maven\033[0m"
    exit 1
fi

MVN_PATH=$(command -v mvn)
echo -e "\033[32mJava: $JAVA_HOME\033[0m"
echo -e "\033[32mMaven: $MVN_PATH\033[0m"
echo ""

echo "Building application..."
mvn clean package -DskipTests -q

if [ $? -ne 0 ]; then
    echo "ERROR: Build failed!"
    exit 1
fi

echo ""
echo "Starting Oracle AI with Voice Assistant..."
echo "Listening for wake word... (Press Ctrl+C to stop)"
echo ""

mvn spring-boot:run
# java -jar target/oracleai-0.0.1-SNAPSHOT.jar
