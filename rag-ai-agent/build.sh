#!/bin/bash

# Build and deploy RAG AI Agent to GCP VM

set -e

echo "====================================="
echo "Building RAG AI Agent"
echo "====================================="

# Build the application
mvn clean package -DskipTests

echo ""
echo "====================================="
echo "Build complete!"
echo "====================================="
echo ""
echo "To deploy to GCP VM:"
echo ""
echo "1. Copy to VM:"
echo "   scp target/rag-ai-agent-1.0.0.jar user@34.48.146.146:~/"
echo ""
echo "2. SSH into VM:"
echo "   ssh user@34.48.146.146"
echo ""
echo "3. Run the application:"
echo "   nohup java -jar rag-ai-agent-1.0.0.jar > app.log 2>&1 &"
echo ""
echo "4. Check logs:"
echo "   tail -f app.log"
echo ""
echo "5. Test the API:"
echo "   curl http://localhost:8080/api/v1/health"
echo ""
echo "====================================="
