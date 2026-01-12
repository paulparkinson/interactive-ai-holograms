#!/bin/bash
# Start the Oracle AI Database RAG API

# Load environment variables
set -a
source .env
set +a

# Start FastAPI server with uvicorn
echo "Starting Oracle AI Database RAG API on http://0.0.0.0:8000"
echo "OpenAPI docs available at: http://0.0.0.0:8000/docs"
echo "Alternative docs at: http://0.0.0.0:8000/redoc"

uvicorn oracle_ai_database_rag:app --host 0.0.0.0 --port 8000 --reload
