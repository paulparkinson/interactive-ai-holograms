# Set the environment variables - EXAMPLE FILE
# Copy this file to run_aiholo.ps1 and replace with your actual values

$env:SANDBOX_API_URL = "http://YOUR_SANDBOX_SERVER/v1/chat/completions?client=server"
$env:AI_OPTIMZER = "Bearer YOUR_AI_OPTIMIZER_TOKEN_HERE"
$env:OPENAI_KEY = "sk-YOUR_OPENAI_API_KEY_HERE"
$env:OPENAI_API_KEY = "sk-YOUR_OPENAI_API_KEY_HERE"
$env:OPENAI_MODEL = "gpt-4"
$env:OCI_VISION_ENDPOINT = "https://vision.aiservice.YOUR_REGION.oci.oraclecloud.com/20220125"
$env:OCI_COMPARTMENT_ID = "ocid1.compartment.oc1..YOUR_COMPARTMENT_ID"
$env:OCI_API_KEY = "YOUR_OCI_API_KEY_OPTIONAL"
$env:AIHOLO_HOST_URL = "http://localhost:8080"
$env:AUDIO_DIR_PATH = "C:/path/to/your/project/src/main/resources/static/audio-aiholo/"
$env:LANGFLOW_SERVER_URL = "http://YOUR_LANGFLOW_SERVER:7860/api"
$env:LANGFLOW_FLOW_ID = "your-langflow-flow-id-here"
$env:LANGFLOW_API_KEY = "sk-YOUR_LANGFLOW_API_KEY_HERE"
$env:IS_AUDIO2FACE = "false"
# TTS configuration examples
$env:TTS_ENGINE = "COQUI"
$env:TTS_QUALITY = "BALANCED"
$env:COQUI_TTS_VERBOSE = "false"
$env:DB_USER = "YOUR_DATABASE_USERNAME"
$env:DB_PASSWORD = "YOUR_DATABASE_PASSWORD"
# For Oracle Autonomous Database with wallet, use format:
# $env:DB_URL = "jdbc:oracle:thin:@dbname_high?TNS_ADMIN=C:/path/to/wallet"
# For example: $env:DB_URL = "jdbc:oracle:thin:@aiholodb_high?TNS_ADMIN=C:/Users/user/Downloads/Wallet_aiholodb"
# Make sure the TNS entry name (e.g., aiholodb_high) exists in your wallet's tnsnames.ora
$env:DB_URL = "jdbc:oracle:thin:@YOUR_DB_SERVICE?TNS_ADMIN=C:/path/to/your/wallet"
$env:OUTPUT_FILE_PATH = "C:/path/to/your/aiholo_output.txt"
$env:SERVER_PORT = "8080"
$env:SSL_ENABLED = "false"
$env:SSL_KEY_STORE = "classpath:your-keystore.p12"
$env:SSL_KEY_STORE_PASSWORD = "YOUR_KEYSTORE_PASSWORD"
$env:SSL_KEY_ALIAS = "your-key-alias"

# Build and run the application
mvn clean package
java -jar .\target\oracleai-0.0.1-SNAPSHOT.jar