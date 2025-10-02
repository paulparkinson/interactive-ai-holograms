@echo off
REM Set the environment variables - EXAMPLE FILE
REM Copy this file to run_aiholo.bat and replace with your actual values

set SANDBOX_API_URL=http://YOUR_SANDBOX_SERVER/v1/chat/completions?client=server
set AI_OPTIMZER=Bearer YOUR_AI_OPTIMIZER_TOKEN_HERE
set AIHOLO_HOST_URL=http://localhost:8080
set AUDIO_DIR_PATH=C:\path\to\your\project\src\main\resources\static\audio-aiholo\
set LANGFLOW_SERVER_URL=http://YOUR_LANGFLOW_SERVER:7860/api
set LANGFLOW_FLOW_ID=your-langflow-flow-id-here
set LANGFLOW_API_KEY=sk-YOUR_LANGFLOW_API_KEY_HERE
set IS_AUDIO2FACE=false
set DB_USER=YOUR_DATABASE_USERNAME
set DB_PASSWORD=YOUR_DATABASE_PASSWORD
set DB_URL=jdbc:oracle:thin:@YOUR_DB_SERVICE?TNS_ADMIN=C:\path\to\your\wallet

REM Build and run the application
mvn clean package
java -jar .\target\oracleai-0.0.1-SNAPSHOT.jar