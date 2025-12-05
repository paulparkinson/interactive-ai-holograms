# PowerShell version of start.sh
# This script will create a .env and bring up the stack (Oracle DB, vector worker, Spring app).

$ErrorActionPreference = "Stop"

Write-Host "This script will create a .env and bring up the stack (Oracle DB, vector worker, Spring app)."

# Prompt for container runtime
$CONTAINER_RUNTIME = Read-Host "Container runtime (docker or podman, default: docker)"
if ([string]::IsNullOrEmpty($CONTAINER_RUNTIME)) {
    $CONTAINER_RUNTIME = "docker"
}

# Validate container runtime
if ($CONTAINER_RUNTIME -ne "docker" -and $CONTAINER_RUNTIME -ne "podman") {
    Write-Error "Error: CONTAINER_RUNTIME must be 'docker' or 'podman'"
    exit 1
}

Write-Host "Using $CONTAINER_RUNTIME as container runtime"

# Prompt for Oracle DB admin user
$ORACLE_USER = Read-Host "Oracle DB admin user (default: PDBADMIN)"
if ([string]::IsNullOrEmpty($ORACLE_USER)) {
    $ORACLE_USER = "PDBADMIN"
}

# Prompt for Oracle DB password (secure string)
$securePassword = Read-Host "Oracle DB password (will be used for the container 'ORACLE_PWD')" -AsSecureString
$BSTR = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
$ORACLE_PWD = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto($BSTR)
[System.Runtime.InteropServices.Marshal]::ZeroFreeBSTR($BSTR)

if ([string]::IsNullOrEmpty($ORACLE_PWD)) {
    Write-Error "Password cannot be empty."
    exit 1
}

# Prompt for Oracle DB service name
$ORACLE_SERVICE = Read-Host "Oracle DB service name (default: FREEPDB1)"
if ([string]::IsNullOrEmpty($ORACLE_SERVICE)) {
    $ORACLE_SERVICE = "FREEPDB1"
}

# Prompt for Oracle data directory
$ORACLE_DATA_DIR = Read-Host "Host path for Oracle data (absolute, default: C:\data\oracle)"
if ([string]::IsNullOrEmpty($ORACLE_DATA_DIR)) {
    $ORACLE_DATA_DIR = "C:\data\oracle"
}

# Prompt for text model download
$INSTALL_TEXT_MODEL = Read-Host "Download default text model for PDFs/text? (y/n)"
if ([string]::IsNullOrEmpty($INSTALL_TEXT_MODEL)) {
    $INSTALL_TEXT_MODEL = "n"
}

# Prompt for image model download
$INSTALL_IMAGE_MODEL = Read-Host "Download image embedding model for images? (y/n)"
if ([string]::IsNullOrEmpty($INSTALL_IMAGE_MODEL)) {
    $INSTALL_IMAGE_MODEL = "n"
}

# Create directories
New-Item -ItemType Directory -Force -Path $ORACLE_DATA_DIR | Out-Null
New-Item -ItemType Directory -Force -Path ".\models" | Out-Null

# Download text model if requested
if ($INSTALL_TEXT_MODEL -match "^[Yy]") {
    Write-Host "Default text model: sentence-transformers/all-MiniLM-L6-v2 (ONNX)"
    $TEXT_MODEL_URL = Read-Host "Text model URL (press Enter for default, or provide custom URL)"
    if ([string]::IsNullOrEmpty($TEXT_MODEL_URL)) {
        $TEXT_MODEL_URL = "https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/onnx/model.onnx"
    }
    if (![string]::IsNullOrEmpty($TEXT_MODEL_URL)) {
        Write-Host "Downloading text ONNX model from $TEXT_MODEL_URL ..."
        try {
            Invoke-WebRequest -Uri $TEXT_MODEL_URL -OutFile ".\models\text_model.onnx"
        } catch {
            Write-Warning "Text model download failed; will use fallback embeddings."
        }
    } else {
        Write-Host "Skipping text model download."
    }
}

# Download image model if requested
if ($INSTALL_IMAGE_MODEL -match "^[Yy]") {
    Write-Host "Default image model: OpenAI CLIP ViT-B/32 (ONNX)"
    $IMAGE_MODEL_URL = Read-Host "Image model URL (press Enter for default, or provide custom URL)"
    if ([string]::IsNullOrEmpty($IMAGE_MODEL_URL)) {
        $IMAGE_MODEL_URL = "https://huggingface.co/Xenova/clip-vit-base-patch32/resolve/main/onnx/vision_model.onnx"
    }
    if (![string]::IsNullOrEmpty($IMAGE_MODEL_URL)) {
        Write-Host "Downloading image ONNX model from $IMAGE_MODEL_URL ..."
        try {
            Invoke-WebRequest -Uri $IMAGE_MODEL_URL -OutFile ".\models\image_model.onnx"
        } catch {
            Write-Warning "Image model download failed; will use fallback embeddings."
        }
    } else {
        Write-Host "Skipping image model download."
    }
}

# Prompt for vector search mode
$USE_VECTOR = Read-Host "Use Oracle 26ai+ native VECTOR search? (y/n, default: y)"
if ([string]::IsNullOrEmpty($USE_VECTOR)) {
    $USE_VECTOR = "y"
}
if ($USE_VECTOR -match "^[Yy]$") {
    $USE_ORACLE_VECTOR_SEARCH = "true"
    Write-Host "Will use Oracle VECTOR type and VECTOR_DISTANCE() for search"
} else {
    $USE_ORACLE_VECTOR_SEARCH = "false"
    Write-Host "Will use Python-based cosine similarity (legacy CLOB storage)"
}

# Create .env file
$envContent = @"
ORACLE_PWD=$ORACLE_PWD
ORACLE_USER=$ORACLE_USER
ORACLE_SERVICE=$ORACLE_SERVICE
ORACLE_DATA_DIR=$ORACLE_DATA_DIR
MODEL_PATH_TEXT=/models/text_model.onnx
MODEL_PATH_IMAGE=/models/image_model.onnx
USE_ORACLE_VECTOR_SEARCH=$USE_ORACLE_VECTOR_SEARCH
CONTAINER_RUNTIME=$CONTAINER_RUNTIME
"@

Set-Content -Path ".env" -Value $envContent

Write-Host ".env written. Starting $CONTAINER_RUNTIME compose (build if necessary)."

# Start container compose
& $CONTAINER_RUNTIME compose up -d --build

Write-Host "All services started."
Write-Host "- Oracle DB: localhost:1521"
Write-Host "- Python app: http://localhost:8001"
Write-Host "- Spring app: http://localhost:8080"
Write-Host "- Vector search mode: $USE_ORACLE_VECTOR_SEARCH"

Write-Host "Note: The vector worker will attempt to connect to Oracle using the provided credentials."
Write-Host "If the Oracle service name in your DB image differs, update .env ORACLE_SERVICE accordingly."
