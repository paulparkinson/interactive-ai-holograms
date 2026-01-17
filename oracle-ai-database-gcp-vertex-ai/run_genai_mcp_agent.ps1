# Run Oracle AI Database Agent with GenerativeModel + Manual MCP
# This version bypasses ADK's buggy McpToolset and uses manual MCP integration

Write-Host "Starting Oracle AI Database Agent (GenerativeModel + Manual MCP)..." -ForegroundColor Cyan
Write-Host ""

# Set environment variables
$env:ORACLE_RAG_API_URL = "http://34.48.146.146:8501"
$env:GCP_PROJECT_ID = "adb-pm-prod"
$env:GCP_REGION = "us-central1"
$env:SQLCL_PATH = "/opt/sqlcl/bin/sql"
$env:TNS_ADMIN = "$HOME/wallet"

# Activate Python environment if needed
if (Test-Path "venv") {
    .\venv\Scripts\Activate.ps1
}

# Run the agent
python oracle_ai_database_genai_mcp.py
