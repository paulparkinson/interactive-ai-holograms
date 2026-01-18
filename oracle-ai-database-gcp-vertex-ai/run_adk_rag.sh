#!/bin/bash
# Run Oracle AI Database ADK RAG Agent
# Uses modern ADK (LlmAgent) with custom RAG tool - no MCP

echo "Starting Oracle ADK RAG Agent (Documentation Search with ADK)..."
echo ""

# Set environment variables
export ORACLE_RAG_API_URL="http://localhost:8501"
export GCP_PROJECT_ID="adb-pm-prod"
export GCP_REGION="us-central1"

# Activate Python environment if needed
if [ -d "venv" ]; then
    source venv/bin/activate
fi

# Run the agent
python oracle_ai_database_adk_rag.py
