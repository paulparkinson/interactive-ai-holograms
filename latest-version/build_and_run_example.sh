#!/bin/bash
# Set the environment variables - EXAMPLE FILE
# Copy this file to run_aiholo.sh and replace with your actual values

export SANDBOX_API_URL="http://YOUR_SANDBOX_SERVER/v1/chat/completions?client=server"
export AI_OPTIMZER="Bearer YOUR_AI_OPTIMIZER_TOKEN_HERE"
export OPENAI_KEY="sk-YOUR_OPENAI_API_KEY_HERE"
export OPENAI_API_KEY="sk-YOUR_OPENAI_API_KEY_HERE"
export OPENAI_MODEL="gpt-4"
export OCI_VISION_ENDPOINT="https://vision.aiservice.YOUR_REGION.oci.oraclecloud.com/20220125"
export OCI_COMPARTMENT_ID="ocid1.compartment.oc1..YOUR_COMPARTMENT_ID"
export OCI_API_KEY="YOUR_OCI_API_KEY_OPTIONAL"
export AIHOLO_HOST_URL="http://localhost:8080"
export AUDIO_DIR_PATH="/path/to/your/project/src/main/resources/static/audio-aiholo/"
export LANGFLOW_SERVER_URL="http://YOUR_LANGFLOW_SERVER:7860/api"
export LANGFLOW_FLOW_ID="your-langflow-flow-id-here"
export LANGFLOW_API_KEY="sk-YOUR_LANGFLOW_API_KEY_HERE"
export IS_AUDIO2FACE="false"
export TTS_ENGINE="COQUI"
export TTS_QUALITY="BALANCED"
export COQUI_TTS_VERBOSE="false"
export DB_USER="YOUR_DATABASE_USERNAME"
export DB_PASSWORD="YOUR_DATABASE_PASSWORD"
# For Oracle Autonomous Database with wallet, use format:
# export DB_URL="jdbc:oracle:thin:@dbname_high?TNS_ADMIN=/path/to/wallet"
# For example: export DB_URL="jdbc:oracle:thin:@aiholodb_high?TNS_ADMIN=/home/user/Wallet_aiholodb"
# Make sure the TNS entry name (e.g., aiholodb_high) exists in your wallet's tnsnames.ora
export DB_URL="jdbc:oracle:thin:@YOUR_DB_SERVICE?TNS_ADMIN=/path/to/your/wallet"
export OUTPUT_FILE_PATH="/path/to/your/aiholo_output.txt"
export SERVER_PORT="8080"
export SSL_ENABLED="false"
export SSL_KEY_STORE="classpath:your-keystore.p12"
export SSL_KEY_STORE_PASSWORD="YOUR_KEYSTORE_PASSWORD"
export SSL_KEY_ALIAS="your-key-alias"

# Build and run the application
mvn clean package
java -jar ./target/oracleai-0.0.1-SNAPSHOT.jar