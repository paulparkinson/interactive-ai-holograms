@echo off
REM Build and package RAG AI Agent

echo =====================================
echo Building RAG AI Agent
echo =====================================
echo.

call mvn clean package -DskipTests

if %ERRORLEVEL% NEQ 0 (
    echo Build failed!
    exit /b 1
)

echo.
echo =====================================
echo Build complete!
echo =====================================
echo.
echo JAR file created: target\rag-ai-agent-1.0.0.jar
echo.
echo To run locally:
echo   java -jar target\rag-ai-agent-1.0.0.jar
echo.
echo To deploy to GCP VM:
echo   scp target\rag-ai-agent-1.0.0.jar user@34.48.146.146:~/
echo   ssh user@34.48.146.146
echo   nohup java -jar rag-ai-agent-1.0.0.jar ^> app.log 2^>^&1 ^&
echo.
echo =====================================
