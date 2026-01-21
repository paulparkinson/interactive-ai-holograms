#!/bin/bash

# Run Oracle AI Database Agent with ADK + MCP Toolbox (Docker version)
# Uses Docker to run MCP Toolbox server (works on ARM64 and AMD64)

set -e  # Exit on error

# Load environment variables
if [ -f .env ]; then
    export $(cat .env | grep -v '^#' | xargs)
fi

# Set GCP environment
export GCP_PROJECT_ID="${GCP_PROJECT_ID:-adb-pm-prod}"
export GCP_REGION="${GCP_REGION:-us-central1}"
export TOOLBOX_URL="http://host.docker.internal:5000"
export TOOLBOX_HOST_URL="http://127.0.0.1:5000"

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${BLUE}================================================${NC}"
echo -e "${BLUE}Oracle AI Database Agent - MCP Toolbox Edition${NC}"
echo -e "${BLUE}        (Docker-based for ARM64/AMD64)${NC}"
echo -e "${BLUE}================================================${NC}"
echo ""

# Check Docker
if ! command -v docker &> /dev/null; then
    echo -e "${RED}❌ Docker is required but not installed${NC}"
    exit 1
fi

echo -e "${GREEN}✓${NC} Docker found: $(docker --version)"

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

# Function to cleanup
cleanup() {
    echo ""
    echo -e "${YELLOW}Shutting down...${NC}"
    if [ ! -z "$CONTAINER_ID" ]; then
        docker stop $CONTAINER_ID >/dev/null 2>&1 || true
        docker rm $CONTAINER_ID >/dev/null 2>&1 || true
        echo -e "${GREEN}✓${NC} Toolbox container stopped"
    fi
}

# Set trap to cleanup on exit
trap cleanup EXIT INT TERM

# Start Toolbox server in Docker
echo -e "${BLUE}Starting MCP Toolbox server (Docker)...${NC}"
echo "  → Configuration: tools.yaml"
echo "  → Server URL: ${TOOLBOX_HOST_URL}"
echo ""

# Get absolute path to wallet directory for Docker volume mount
WALLET_ABS_PATH=$(realpath "${DB_WALLET_DIR}")

# Start container
echo "  → Pulling/starting ghcr.io/googleapis/genai-toolbox..."
CONTAINER_ID=$(docker run -d \
    --name toolbox-server-$$ \
    -p 5000:5000 \
    -v "$(pwd)/tools.yaml:/app/tools.yaml:ro" \
    -v "${WALLET_ABS_PATH}:${DB_WALLET_DIR}:ro" \
    -e DB_USERNAME="${DB_USERNAME}" \
    -e DB_PASSWORD="${DB_PASSWORD}" \
    -e DB_DSN="${DB_DSN}" \
    -e DB_WALLET_DIR="${DB_WALLET_DIR}" \
    ghcr.io/googleapis/genai-toolbox:latest \
    --tools-file /app/tools.yaml)

if [ -z "$CONTAINER_ID" ]; then
    echo -e "${RED}❌ Failed to start Docker container${NC}"
    exit 1
fi

echo -e "${GREEN}✓${NC} Container started: $CONTAINER_ID"

# Wait for Toolbox to start
echo "Waiting for Toolbox server to start..."
for i in {1..30}; do
    if curl -s ${TOOLBOX_HOST_URL}/health > /dev/null 2>&1; then
        echo -e "${GREEN}✓${NC} Toolbox server is ready"
        break
    fi
    if [ $i -eq 30 ]; then
        echo -e "${RED}❌ Timeout waiting for Toolbox server${NC}"
        echo "Container logs:"
        docker logs $CONTAINER_ID
        exit 1
    fi
    sleep 1
    echo "  Waiting... ($i/30)"
done

echo ""
echo -e "${BLUE}Starting ADK Agent...${NC}"
echo ""

# Update TOOLBOX_URL for the Python agent to use host network
export TOOLBOX_URL="${TOOLBOX_HOST_URL}"

# Run the agent
python oracle_ai_database_adk_mcp_agent.py

# Cleanup is handled by trap
