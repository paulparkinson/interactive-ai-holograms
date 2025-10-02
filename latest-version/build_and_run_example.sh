#!/bin/bash
# Set the environment variables - EXAMPLE FILE
# Copy this file to run_aiholo.sh and replace with your actual values

export SANDBOX_API_URL0="http://YOUR_SANDBOX_SERVER:8000/v1/chat/completions"
export SANDBOX_API_URL="http://YOUR_SANDBOX_SERVER/v1/chat/completions?client=server"
export SANDBOX_AUTH_TOKEN0="Bearer YOUR_SANDBOX_AUTH_TOKEN_HERE"
export AI_OPTIMZER="Bearer YOUR_AI_OPTIMIZER_TOKEN_HERE"
export AIHOLO_HOST_URL="http://localhost:8080"
export AUDIO_DIR_PATH="/path/to/your/project/src/main/resources/static/audio-aiholo/"
export LANGFLOW_SERVER_URL="http://YOUR_LANGFLOW_SERVER:7860/api"
export LANGFLOW_FLOW_ID="your-langflow-flow-id-here"
export LANGFLOW_API_KEY="sk-YOUR_LANGFLOW_API_KEY_HERE"
export IS_AUDIO2FACE="false"

# Build and run the application
mvn clean package
java -jar ./target/oracleai-0.0.1-SNAPSHOT.jar