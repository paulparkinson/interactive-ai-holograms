#!/bin/bash
# Run Oracle AI Database ADK RAG Agent
# Uses modern ADK (LlmAgent) with direct Oracle vector store connection

echo "Starting Oracle ADK RAG Agent (Direct Vector Store)..."
echo ""

# Set environment variables for GCP
export GCP_PROJECT_ID="adb-pm-prod"
export GCP_REGION="us-central1"

# Note: Database credentials loaded from .env file
# Make sure you've run: gcloud auth application-default login

# Activate Python environment if needed
if [ -d "../.venv" ]; then
    source ../.venv/bin/activate
elif [ -d "venv" ]; then
    source venv/bin/activate
fi

# Run the agent
python oracle_ai_database_adk_agent.py
