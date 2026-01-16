# Run Oracle AI Database ADK Agent with MCP Integration

# Set environment variables if not already set
if (-not $env:ORACLE_RAG_API_URL) { $env:ORACLE_RAG_API_URL = "http://34.48.146.146:8501" }
if (-not $env:GCP_PROJECT_ID) { $env:GCP_PROJECT_ID = "adb-pm-prod" }
if (-not $env:GCP_REGION) { $env:GCP_REGION = "us-central1" }
if (-not $env:SQLCL_PATH) { $env:SQLCL_PATH = "/opt/sqlcl/bin/sql" }
if (-not $env:TNS_ADMIN) { $env:TNS_ADMIN = "$env:HOME/wallet" }

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "Oracle AI Database ADK Agent with MCP" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "RAG API: $env:ORACLE_RAG_API_URL"
Write-Host "SQLcl: $env:SQLCL_PATH"
Write-Host "Wallet: $env:TNS_ADMIN"
Write-Host "MCP Connection: paulparkdb_mcp"
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host ""

# Activate virtual environment if it exists
if (Test-Path "venv\Scripts\Activate.ps1") {
    & venv\Scripts\Activate.ps1
}

# Run the agent
python oracle_ai_database_adk_agent.py
