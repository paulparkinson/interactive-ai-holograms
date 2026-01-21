#!/bin/bash

# Run Oracle AI Database Agent with ADK + MCP Toolbox
# This script:
# 1. Starts the MCP Toolbox server with tools.yaml configuration
# 2. Runs the ADK agent that connects to the Toolbox server

set -e  # Exit on error

# Load environment variables
if [ -f .env ]; then
    export $(cat .env | grep -v '^#' | xargs)
fi

# Set GCP environment
export GCP_PROJECT_ID="${GCP_PROJECT_ID:-adb-pm-prod}"
export GCP_REGION="${GCP_REGION:-us-central1}"
export TOOLBOX_URL="http://127.0.0.1:5000"

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${BLUE}================================================${NC}"
echo -e "${BLUE}Oracle AI Database Agent - MCP Toolbox Edition${NC}"
echo -e "${BLUE}================================================${NC}"
echo ""

# Check if Toolbox binary exists or if npx is available
if command -v npx &> /dev/null; then
    echo -e "${GREEN}✓${NC} Using npx to run Toolbox server"
    TOOLBOX_CMD="npx @toolbox-sdk/server"
elif ! command -v toolbox &> /dev/null; then
    echo -e "${YELLOW}⚠️  Toolbox binary not found!${NC}"
    echo ""
    echo "Installing MCP Toolbox for Databases..."
    echo ""
    
    # Detect OS and architecture
    ARCH=$(uname -m)
    OS="linux/amd64"
    
    if [[ "$OSTYPE" == "darwin"* ]]; then
        if [[ "$ARCH" == "arm64" ]]; then
            OS="darwin/arm64"
        else
            OS="darwin/amd64"
        fi
    elif [[ "$ARCH" == "aarch64" ]] || [[ "$ARCH" == "arm64" ]]; then
        OS="linux/arm64"
    fi
    
    # Download toolbox
    VERSION="0.24.0"
    echo "  → Downloading Toolbox v${VERSION} for ${OS}"
    curl -L -o toolbox "https://storage.googleapis.com/genai-toolbox/v${VERSION}/${OS}/toolbox"
    chmod +x toolbox
    echo -e "  ${GREEN}✓${NC} Toolbox binary downloaded"
    echo ""
    TOOLBOX_CMD="./toolbox"
else
    TOOLBOX_CMD="toolbox"
    echo -e "${GREEN}✓${NC} Toolbox binary found"
fi

# Activate virtual environment
if [ -d "../.venv" ]; then
    echo -e "${GREEN}✓${NC} Activating ../.venv"
    source ../.venv/bin/activate
elif [ -d "venv" ]; then
    echo -e "${GREEN}✓${NC} Activating venv"
    source venv/bin/activate
else
    echo -e "${YELLOW}⚠️  No virtual environment found. Using system Python.${NC}"
fi

# Check for required Python packages
echo ""
echo "Checking Python dependencies..."
if ! python -c "import google.adk" 2>/dev/null; then
    echo -e "${YELLOW}⚠️  google-adk not installed${NC}"
    echo "  Installing: pip install google-adk"
    pip install google-adk
fi

if ! python -c "import toolbox_core" 2>/dev/null; then
    echo -e "${YELLOW}⚠️  toolbox-core not installed${NC}"
    echo "  Installing: pip install toolbox-core"
    pip install toolbox-core
fi

echo -e "${GREEN}✓${NC} All dependencies available"
echo ""

# Function to cleanup background processes
cleanup() {
    echo ""
    echo -e "${YELLOW}Shutting down...${NC}"
    if [ ! -z "$TOOLBOX_PID" ]; then
        kill $TOOLBOX_PID 2>/dev/null || true
        echo -e "${GREEN}✓${NC} Toolbox server stopped"
    fi
}

# Set trap to cleanup on exit
trap cleanup EXIT INT TERM

# Start Toolbox server in background
echo -e "${BLUE}Starting MCP Toolbox server...${NC}"
echo "  → Configuration: tools.yaml"
echo "  → Server URL: ${TOOLBOX_URL}"
echo ""

$TOOLBOX_CMD --tools-file tools.yaml > toolbox.log 2>&1 &
TOOLBOX_PID=$!

# Wait for Toolbox to start
echo "Waiting for Toolbox server to start..."
for i in {1..10}; do
    if curl -s http://127.0.0.1:5000/health > /dev/null 2>&1; then
        echo -e "${GREEN}✓${NC} Toolbox server is ready"
        break
    fi
    sleep 1
    echo "  Waiting... ($i/10)"
done

if ! curl -s http://127.0.0.1:5000/health > /dev/null 2>&1; then
    echo -e "${RED}❌ Failed to start Toolbox server${NC}"
    echo "Check toolbox.log for details:"
    tail -20 toolbox.log
    exit 1
fi

echo ""
echo -e "${BLUE}Starting ADK Agent...${NC}"
echo ""

# Run the agent
python oracle_ai_database_adk_mcp_agent.py

# Cleanup is handled by trap
