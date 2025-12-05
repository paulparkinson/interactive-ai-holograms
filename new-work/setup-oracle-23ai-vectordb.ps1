# Oracle Database 26ai AI Vector Search Setup Script
# Based on: https://blogs.oracle.com/database/getting-started-with-oracle-database-26ai-ai-vector-search

Write-Host "======================================" -ForegroundColor Cyan
Write-Host "Oracle Database 26ai Vector Search Setup" -ForegroundColor Cyan
Write-Host "======================================" -ForegroundColor Cyan

# Prompt for container engine selection
Write-Host "`nSelect container engine:" -ForegroundColor Cyan
Write-Host "  1. Docker" -ForegroundColor White
Write-Host "  2. Podman" -ForegroundColor White
$choice = Read-Host "Enter your choice (1 or 2)"

if ($choice -eq "2") {
    $CONTAINER_ENGINE = "podman"
    Write-Host "Using Podman" -ForegroundColor Green
} elseif ($choice -eq "1") {
    $CONTAINER_ENGINE = "docker"
    Write-Host "Using Docker" -ForegroundColor Green
} else {
    Write-Host "Invalid choice. Defaulting to Docker" -ForegroundColor Yellow
    $CONTAINER_ENGINE = "docker"
}

# Configuration
$CONTAINER_NAME = "oracle26ai"
$ORACLE_PWD = "Welcome126456"
$HOST_PORT = 1521
$ONNX_MODEL_URL = "https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/onnx/model.onnx"
$ONNX_MODEL_FILE = "all-MiniLM-L6-v2.onnx"

# Step 1: Pull Oracle Database 26ai Free container
Write-Host "`n[Step 1/6] Pulling Oracle Database 26ai Free container..." -ForegroundColor Yellow
& $CONTAINER_ENGINE pull container-registry.oracle.com/database/free:latest

if ($LASTEXITCODE -ne 0) {
    Write-Host "Error: Failed to pull Oracle Database container" -ForegroundColor Red
    exit 1
}

# Step 2: Check if container already exists and remove if needed
Write-Host "`n[Step 2/6] Checking for existing container..." -ForegroundColor Yellow
$existingContainer = & $CONTAINER_ENGINE ps -a --filter "name=$CONTAINER_NAME" --format "{{.Names}}"
if ($existingContainer -eq $CONTAINER_NAME) {
    Write-Host "Removing existing container: $CONTAINER_NAME" -ForegroundColor Yellow
    & $CONTAINER_ENGINE stop $CONTAINER_NAME
    & $CONTAINER_ENGINE rm $CONTAINER_NAME
}

# Step 3: Run Oracle Database container
Write-Host "`n[Step 3/6] Starting Oracle Database 26ai container..." -ForegroundColor Yellow
Write-Host "Container name: $CONTAINER_NAME" -ForegroundColor Gray
Write-Host "Port mapping: $HOST_PORT`:1521" -ForegroundColor Gray
Write-Host "This may take a few minutes on first startup..." -ForegroundColor Gray

& $CONTAINER_ENGINE run -d `
    --name $CONTAINER_NAME `
    -p ${HOST_PORT}:1521 `
    -e ORACLE_PWD=$ORACLE_PWD `
    -e ORACLE_CHARACTERSET=AL32UTF8 `
    container-registry.oracle.com/database/free:latest

if ($LASTEXITCODE -ne 0) {
    Write-Host "Error: Failed to start Oracle Database container" -ForegroundColor Red
    exit 1
}

# Step 4: Wait for database to be ready
Write-Host "`n[Step 4/6] Waiting for database to be ready..." -ForegroundColor Yellow
Write-Host "This can take 2-5 minutes..." -ForegroundColor Gray

$maxAttempts = 60
$attempt = 0
$dbReady = $false

while ($attempt -lt $maxAttempts -and -not $dbReady) {
    $attempt++
    Start-Sleep -Seconds 5
    
    # Check container logs for "DATABASE IS READY TO USE!"
    $logs = & $CONTAINER_ENGINE logs $CONTAINER_NAME 2>&1
    if ($logs -match "DATABASE IS READY TO USE!") {
        $dbReady = $true
        Write-Host "Database is ready!" -ForegroundColor Green
    } else {
        Write-Host "Waiting... (attempt $attempt/$maxAttempts)" -ForegroundColor Gray
    }
}

if (-not $dbReady) {
    Write-Host "Error: Database did not become ready in time" -ForegroundColor Red
    Write-Host "Check logs with: $CONTAINER_ENGINE logs $CONTAINER_NAME" -ForegroundColor Yellow
    exit 1
}

# Step 5: Download ONNX model
Write-Host "`n[Step 5/6] Downloading ONNX embedding model..." -ForegroundColor Yellow
Write-Host "Model: all-MiniLM-L6-v2 (sentence-transformers)" -ForegroundColor Gray

if (Test-Path $ONNX_MODEL_FILE) {
    Write-Host "Model file already exists, skipping download" -ForegroundColor Gray
} else {
    try {
        Invoke-WebRequest -Uri $ONNX_MODEL_URL -OutFile $ONNX_MODEL_FILE
        Write-Host "Model downloaded successfully" -ForegroundColor Green
    } catch {
        Write-Host "Error downloading ONNX model: $_" -ForegroundColor Red
        exit 1
    }
}

# Copy ONNX model to container
Write-Host "Copying ONNX model to container..." -ForegroundColor Gray
& $CONTAINER_ENGINE cp $ONNX_MODEL_FILE ${CONTAINER_NAME}:/opt/oracle/

# Step 6: Setup database with vector search
Write-Host "`n[Step 6/6] Setting up AI Vector Search..." -ForegroundColor Yellow

# Create SQL setup script
$sqlSetup = @"
-- Connect as SYSTEM user
CONNECT system/$ORACLE_PWD@//localhost:1521/FREEPDB1

-- Create demo user for vector search
CREATE USER vectoruser IDENTIFIED BY $ORACLE_PWD;
GRANT CONNECT, RESOURCE TO vectoruser;
GRANT UNLIMITED TABLESPACE TO vectoruser;
GRANT CREATE VIEW TO vectoruser;
GRANT CREATE MINING MODEL TO vectoruser;

-- Grant necessary privileges for ONNX models
GRANT DB_DEVELOPER_ROLE TO vectoruser;
GRANT EXECUTE ON DBMS_VECTOR TO vectoruser;
GRANT EXECUTE ON DBMS_VECTOR_CHAIN TO vectoruser;

-- Connect as the vector user
CONNECT vectoruser/$ORACLE_PWD@//localhost:1521/FREEPDB1

-- Load ONNX model into database
BEGIN
  DBMS_VECTOR.LOAD_ONNX_MODEL(
    directory => 'DATA_PUMP_DIR',
    file_name => 'all-MiniLM-L6-v2.onnx',
    model_name => 'doc_model',
    metadata => JSON('{"function": "embedding", "embeddingOutput": "embedding", "input": {"input": ["DATA"]}}')
  );
END;
/

-- Create a sample table with vector column
CREATE TABLE documents (
  id NUMBER PRIMARY KEY,
  text VARCHAR2(4000),
  embedding VECTOR(384, FLOAT32)
);

-- Create vector index for similarity search
CREATE VECTOR INDEX doc_vector_idx ON documents (embedding)
ORGANIZATION NEIGHBOR PARTITIONS
WITH DISTANCE COSINE;

-- Insert sample documents
INSERT INTO documents VALUES (1, 'Oracle Database 26ai introduces AI Vector Search', 
  VECTOR_EMBEDDING(doc_model USING 'Oracle Database 26ai introduces AI Vector Search' AS DATA));
  
INSERT INTO documents VALUES (2, 'Vector search enables semantic similarity queries',
  VECTOR_EMBEDDING(doc_model USING 'Vector search enables semantic similarity queries' AS DATA));
  
INSERT INTO documents VALUES (3, 'Machine learning models can be stored in the database',
  VECTOR_EMBEDDING(doc_model USING 'Machine learning models can be stored in the database' AS DATA));

COMMIT;

-- Test vector similarity search
SELECT id, text, VECTOR_DISTANCE(embedding, 
  VECTOR_EMBEDDING(doc_model USING 'database AI features' AS DATA), COSINE) AS similarity
FROM documents
ORDER BY similarity
FETCH FIRST 3 ROWS ONLY;

-- Show model info
SELECT model_name, model_type, attribute_name, data_type
FROM user_mining_model_attributes
WHERE model_name = 'DOC_MODEL'
ORDER BY attribute_name;

EXIT;
"@

# Save SQL script
$sqlSetup | Out-File -FilePath "setup-vector-search.sql" -Encoding ASCII

# Copy SQL script to container
& $CONTAINER_ENGINE cp setup-vector-search.sql ${CONTAINER_NAME}:/opt/oracle/

# Execute SQL setup
Write-Host "Executing SQL setup script..." -ForegroundColor Gray
& $CONTAINER_ENGINE exec -it $CONTAINER_NAME bash -c "sqlplus /nolog @/opt/oracle/setup-vector-search.sql"

# Cleanup
Remove-Item setup-vector-search.sql -ErrorAction SilentlyContinue

# Summary
Write-Host "`n======================================" -ForegroundColor Cyan
Write-Host "Setup Complete!" -ForegroundColor Green
Write-Host "======================================" -ForegroundColor Cyan
Write-Host "`nConnection Details:" -ForegroundColor Yellow
Write-Host "  Host: localhost" -ForegroundColor White
Write-Host "  Port: $HOST_PORT" -ForegroundColor White
Write-Host "  Service: FREEPDB1" -ForegroundColor White
Write-Host "  User: vectoruser" -ForegroundColor White
Write-Host "  Password: $ORACLE_PWD" -ForegroundColor White
Write-Host "`nConnection String:" -ForegroundColor Yellow
Write-Host "  jdbc:oracle:thin:@localhost:${HOST_PORT}/FREEPDB1" -ForegroundColor White
Write-Host "`nUseful Commands:" -ForegroundColor Yellow
Write-Host "  $CONTAINER_ENGINE logs $CONTAINER_NAME          # View container logs" -ForegroundColor Gray
Write-Host "  $CONTAINER_ENGINE exec -it $CONTAINER_NAME bash # Access container shell" -ForegroundColor Gray
Write-Host "  $CONTAINER_ENGINE stop $CONTAINER_NAME          # Stop database" -ForegroundColor Gray
Write-Host "  $CONTAINER_ENGINE start $CONTAINER_NAME         # Start database" -ForegroundColor Gray
Write-Host "`nTo connect with SQL*Plus:" -ForegroundColor Yellow
Write-Host "  $CONTAINER_ENGINE exec -it $CONTAINER_NAME sqlplus vectoruser/$ORACLE_PWD@FREEPDB1" -ForegroundColor Gray
Write-Host ""
