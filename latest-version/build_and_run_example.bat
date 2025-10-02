@echo off
REM Set the environment variables - EXAMPLE FILE
REM Copy this file to run_aiholo.bat and replace with your actual values

set SANDBOX_API_URL0=http://YOUR_SANDBOX_SERVER:8000/v1/chat/completions
set SANDBOX_API_URL=http://YOUR_SANDBOX_SERVER/v1/chat/completions?client=server
set SANDBOX_AUTH_TOKEN0=Bearer YOUR_SANDBOX_AUTH_TOKEN_HERE
set AI_OPTIMZER=Bearer YOUR_AI_OPTIMIZER_TOKEN_HERE
set AIHOLO_HOST_URL=http://localhost:8080
set AUDIO_DIR_PATH=C:\path\to\your\project\src\main\resources\static\audio-aiholo\
set LANGFLOW_SERVER_URL=http://YOUR_LANGFLOW_SERVER:7860/api
set LANGFLOW_FLOW_ID=your-langflow-flow-id-here
set LANGFLOW_API_KEY=sk-YOUR_LANGFLOW_API_KEY_HERE
set IS_AUDIO2FACE=false

REM Build and run the application
mvn clean package
java -jar .\target\oracleai-0.0.1-SNAPSHOT.jar