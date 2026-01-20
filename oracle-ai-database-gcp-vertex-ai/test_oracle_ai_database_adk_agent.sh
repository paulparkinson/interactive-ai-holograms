#!/bin/bash
# Test script for Oracle ADK RAG Agent

cd /home/paul/src/github.com/paulparkinson/interactive-ai-holograms/oracle-ai-database-gcp-vertex-ai

# Activate virtual environment
source ../.venv/bin/activate

echo "Testing Oracle ADK RAG Agent..."
echo ""
echo "Note: You must have already authenticated with:"
echo "  gcloud auth application-default login"
echo ""

# Run the agent with a test question
python3 oracle_ai_database_adk_agent.py << EOF
What is JSON Relational Duality?
quit
EOF
