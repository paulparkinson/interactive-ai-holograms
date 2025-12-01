#!/bin/bash
# Oracle Database 26ai AI Vector Search Setup Script (Linux/Mac)
# Based on: https://blogs.oracle.com/database/getting-started-with-oracle-database-26ai-ai-vector-search

set -e

echo "======================================"
echo "Oracle Database 26ai Vector Search Setup"
echo "======================================"

# Configuration
CONTAINER_NAME="oracle26ai"
ORACLE_PWD="Welcome126456"
HOST_PORT=1521
ONNX_MODEL_URL="https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/onnx/model.onnx"
ONNX_MODEL_FILE="all-MiniLM-L6-v2.onnx"

# Step 1: Pull Oracle Database 26ai Free container
echo -e "\n[Step 1/6] Pulling Oracle Database 26ai Free container..."
docker pull container-registry.oracle.com/database/free:latest

# Step 2: Check if container already exists and remove if needed
echo -e "\n[Step 2/6] Checking for existing container..."
if [ "$(docker ps -a -q -f name=$CONTAINER_NAME)" ]; then
    echo "Removing existing container: $CONTAINER_NAME"
    docker stop $CONTAINER_NAME || true
    docker rm $CONTAINER_NAME || true
fi

# Step 3: Run Oracle Database container
echo -e "\n[Step 3/6] Starting Oracle Database 26ai container..."
echo "Container name: $CONTAINER_NAME"
echo "Port mapping: ${HOST_PORT}:1521"
echo "This may take a few minutes on first startup..."

docker run -d \
    --name $CONTAINER_NAME \
    -p ${HOST_PORT}:1521 \
    -e ORACLE_PWD=$ORACLE_PWD \
    -e ORACLE_CHARACTERSET=AL32UTF8 \
    container-registry.oracle.com/database/free:latest

# Step 4: Wait for database to be ready
echo -e "\n[Step 4/6] Waiting for database to be ready..."
echo "This can take 2-5 minutes..."

max_attempts=60
attempt=0
db_ready=false

while [ $attempt -lt $max_attempts ] && [ "$db_ready" = false ]; do
    attempt=$((attempt + 1))
    sleep 5
    
    # Check container logs for "DATABASE IS READY TO USE!"
    if docker logs $CONTAINER_NAME 2>&1 | grep -q "DATABASE IS READY TO USE!"; then
        db_ready=true
        echo "Database is ready!"
    else
        echo "Waiting... (attempt $attempt/$max_attempts)"
    fi
done

if [ "$db_ready" = false ]; then
    echo "Error: Database did not become ready in time"
    echo "Check logs with: docker logs $CONTAINER_NAME"
    exit 1
fi

# Step 5: Download ONNX model
echo -e "\n[Step 5/6] Downloading ONNX embedding model..."
echo "Model: all-MiniLM-L6-v2 (sentence-transformers)"

if [ -f "$ONNX_MODEL_FILE" ]; then
    echo "Model file already exists, skipping download"
else
    curl -L -o $ONNX_MODEL_FILE $ONNX_MODEL_URL
    echo "Model downloaded successfully"
fi

# Copy ONNX model to container
echo "Copying ONNX model to container..."
docker cp $ONNX_MODEL_FILE ${CONTAINER_NAME}:/opt/oracle/

# Step 6: Setup database with vector search
echo -e "\n[Step 6/6] Setting up AI Vector Search..."

# Create SQL setup script
cat > setup-vector-search.sql << 'EOF'
-- Connect as SYSTEM user
CONNECT system/Welcome126456@//localhost:1521/FREEPDB1

-- Create demo user for vector search
CREATE USER vectoruser IDENTIFIED BY Welcome126456;
GRANT CONNECT, RESOURCE TO vectoruser;
GRANT UNLIMITED TABLESPACE TO vectoruser;
GRANT CREATE VIEW TO vectoruser;
GRANT CREATE MINING MODEL TO vectoruser;

-- Grant necessary privileges for ONNX models
GRANT DB_DEVELOPER_ROLE TO vectoruser;
GRANT EXECUTE ON DBMS_VECTOR TO vectoruser;
GRANT EXECUTE ON DBMS_VECTOR_CHAIN TO vectoruser;

-- Connect as the vector user
CONNECT vectoruser/Welcome126456@//localhost:1521/FREEPDB1

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
EOF

# Copy SQL script to container
docker cp setup-vector-search.sql ${CONTAINER_NAME}:/opt/oracle/

# Execute SQL setup
echo "Executing SQL setup script..."
docker exec -it $CONTAINER_NAME bash -c "sqlplus /nolog @/opt/oracle/setup-vector-search.sql"

# Cleanup
rm -f setup-vector-search.sql

# Summary
echo -e "\n======================================"
echo "Setup Complete!"
echo "======================================"
echo -e "\nConnection Details:"
echo "  Host: localhost"
echo "  Port: $HOST_PORT"
echo "  Service: FREEPDB1"
echo "  User: vectoruser"
echo "  Password: $ORACLE_PWD"
echo -e "\nConnection String:"
echo "  jdbc:oracle:thin:@localhost:${HOST_PORT}/FREEPDB1"
echo -e "\nUseful Commands:"
echo "  docker logs $CONTAINER_NAME          # View container logs"
echo "  docker exec -it $CONTAINER_NAME bash # Access container shell"
echo "  docker stop $CONTAINER_NAME          # Stop database"
echo "  docker start $CONTAINER_NAME         # Start database"
echo -e "\nTo connect with SQL*Plus:"
echo "  docker exec -it $CONTAINER_NAME sqlplus vectoruser/$ORACLE_PWD@FREEPDB1"
echo ""
