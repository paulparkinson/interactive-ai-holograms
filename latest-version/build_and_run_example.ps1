# Set the environment variables - EXAMPLE FILE
# Copy this file to run_aiholo.ps1 and replace with your actual values

$env:SANDBOX_API_URL = "http://YOUR_SANDBOX_SERVER/v1/chat/completions?client=server"
$env:AI_OPTIMZER = "Bearer YOUR_AI_OPTIMIZER_TOKEN_HERE"
$env:AIHOLO_HOST_URL = "http://localhost:8080"
$env:AUDIO_DIR_PATH = "C:/path/to/your/project/src/main/resources/static/audio-aiholo/"
$env:LANGFLOW_SERVER_URL = "http://YOUR_LANGFLOW_SERVER:7860/api"
$env:LANGFLOW_FLOW_ID = "your-langflow-flow-id-here"
$env:LANGFLOW_API_KEY = "sk-YOUR_LANGFLOW_API_KEY_HERE"
$env:IS_AUDIO2FACE = "false"
$env:DB_USER = "YOUR_DATABASE_USERNAME"
$env:DB_PASSWORD = "YOUR_DATABASE_PASSWORD"
$env:DB_URL = "jdbc:oracle:thin:@YOUR_DB_SERVICE?TNS_ADMIN=C:/path/to/your/wallet"

# Build and run the application
mvn clean package
java -jar .\target\oracleai-0.0.1-SNAPSHOT.jar