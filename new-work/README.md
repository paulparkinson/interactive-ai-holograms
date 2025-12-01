# Oracle Database 26ai AI Vector Search Setup

Automated setup scripts for Oracle Database 26ai with AI Vector Search capabilities.

Based on: [Getting Started with Oracle Database 26ai AI Vector Search](https://blogs.oracle.com/database/getting-started-with-oracle-database-26ai-ai-vector-search)

## What These Scripts Do

1. **Pull Oracle Database 26ai Free container** from Oracle Container Registry
2. **Start the database container** with proper configuration
3. **Wait for database initialization** (can take 2-5 minutes)
4. **Download ONNX embedding model** (all-MiniLM-L6-v2 from HuggingFace)
5. **Load the model into the database** using DBMS_VECTOR
6. **Create demo schema** with:
   - `vectoruser` with necessary privileges
   - `documents` table with VECTOR column (384 dimensions)
   - Vector index for similarity search
   - Sample data with embeddings
7. **Run test query** to demonstrate vector similarity search

## Prerequisites

- Docker installed and running
- Internet connection (for pulling container and ONNX model)
- At least 8GB RAM available for Docker
- Ports: 1521 available

## Usage

### Windows (PowerShell)

```powershell
.\setup-oracle-26ai-vectordb.ps1
```

### Linux/Mac (Bash)

```bash
chmod +x setup-oracle-26ai-vectordb.sh
./setup-oracle-26ai-vectordb.sh
```

## Connection Details

After successful setup:

- **Host**: localhost
- **Port**: 1521
- **Service**: FREEPDB1
- **User**: vectoruser
- **Password**: Welcome126456
- **JDBC**: `jdbc:oracle:thin:@localhost:1521/FREEPDB1`

## What's Installed

### ONNX Model
- **Model**: sentence-transformers/all-MiniLM-L6-v2
- **Type**: Sentence embedding model
- **Dimensions**: 384
- **Format**: ONNX
- **Stored as**: `doc_model` in the database

### Sample Schema

```sql
-- Documents table with vector embeddings
CREATE TABLE documents (
  id NUMBER PRIMARY KEY,
  text VARCHAR2(4000),
  embedding VECTOR(384, FLOAT32)
);

-- Vector index for fast similarity search
CREATE VECTOR INDEX doc_vector_idx ON documents (embedding)
ORGANIZATION NEIGHBOR PARTITIONS
WITH DISTANCE COSINE;
```

### Sample Data

Three pre-loaded documents about Oracle AI features with their embeddings.

## Testing Vector Search

Connect to the database:

```bash
docker exec -it oracle26ai sqlplus vectoruser/Welcome126456@FREEPDB1
```

Run a similarity search:

```sql
SELECT id, text, 
  VECTOR_DISTANCE(embedding, 
    VECTOR_EMBEDDING(doc_model USING 'database AI features' AS DATA), 
    COSINE) AS similarity
FROM documents
ORDER BY similarity
FETCH FIRST 3 ROWS ONLY;
```

## Useful Commands

### Container Management

```bash
# View container logs
docker logs oracle26ai

# Access container shell
docker exec -it oracle26ai bash

# Stop database
docker stop oracle26ai

# Start database
docker start oracle26ai

# Remove container
docker stop oracle26ai
docker rm oracle26ai
```

### SQL*Plus Access

```bash
# Connect as vectoruser
docker exec -it oracle26ai sqlplus vectoruser/Welcome126456@FREEPDB1

# Connect as SYSTEM
docker exec -it oracle26ai sqlplus system/Welcome126456@FREEPDB1
```

## Vector Search Examples

### Insert New Document

```sql
INSERT INTO documents VALUES (
  4, 
  'Artificial intelligence and machine learning in Oracle Database',
  VECTOR_EMBEDDING(doc_model USING 'Artificial intelligence and machine learning in Oracle Database' AS DATA)
);
COMMIT;
```

### Semantic Search

```sql
-- Find documents similar to a query
SELECT id, text,
  VECTOR_DISTANCE(embedding,
    VECTOR_EMBEDDING(doc_model USING 'AI and ML capabilities' AS DATA),
    COSINE) AS distance
FROM documents
ORDER BY distance
FETCH FIRST 5 ROWS ONLY;
```

### View Model Information

```sql
SELECT model_name, model_type, attribute_name, data_type
FROM user_mining_model_attributes
WHERE model_name = 'DOC_MODEL'
ORDER BY attribute_name;
```

## Key Features Used

### DBMS_VECTOR Package
- `LOAD_ONNX_MODEL`: Load ONNX models into database
- `VECTOR_EMBEDDING`: Generate embeddings using loaded models

### Vector Data Type
- `VECTOR(384, FLOAT32)`: Native vector storage (384 dimensions, 32-bit floats)

### Vector Index
- **NEIGHBOR PARTITIONS**: Optimized for similarity search
- **COSINE distance**: Semantic similarity metric

### Vector Functions
- `VECTOR_DISTANCE`: Calculate distance between vectors
- Supports COSINE, EUCLIDEAN, DOT metrics

## Troubleshooting

### Container won't start
```bash
# Check if port 1521 is already in use
netstat -an | grep 1521

# View container logs for errors
docker logs oracle26ai
```

### Database not ready
```bash
# Monitor database startup
docker logs -f oracle26ai

# Look for "DATABASE IS READY TO USE!"
```

### Model loading fails
```bash
# Verify model file was copied
docker exec oracle26ai ls -lh /opt/oracle/*.onnx

# Check Oracle directory permissions
docker exec -it oracle26ai sqlplus system/Welcome126456@FREEPDB1
SELECT * FROM dba_directories WHERE directory_name = 'DATA_PUMP_DIR';
```

### Connection refused
```bash
# Verify database is listening
docker exec oracle26ai lsnrctl status

# Check if container is running
docker ps | grep oracle26ai
```

## Additional Resources

- [Oracle Database 26ai AI Vector Search Documentation](https://docs.oracle.com/en/database/oracle/oracle-database/26/vecse/)
- [Oracle Database Free Container](https://container-registry.oracle.com/ords/f?p=113:4:::::4:P4_REPOSITORY,AI_REPOSITORY,AI_REPOSITORY_NAME,P4_REPOSITORY_NAME,P4_EULA_ID,P4_BUSINESS_AREA_ID:9,9,Oracle%20Database%20Free,Oracle%20Database%20Free,1,0)
- [HuggingFace Sentence Transformers](https://huggingface.co/sentence-transformers)
- [Oracle AI Vector Search Blog](https://blogs.oracle.com/database/post/oracle-announces-general-availability-of-ai-vector-search-in-oracle-database-26ai)

## Notes

- The Oracle Database 26ai Free container is limited to 12GB RAM and 2 CPUs
- ONNX models are stored in the database and persist across restarts
- Vector indexes improve query performance for large datasets
- The all-MiniLM-L6-v2 model provides a good balance of speed and accuracy for general text

## Security Note

The default password (`Welcome126456`) is for demo purposes only. In production:
- Use strong passwords
- Change default passwords immediately
- Secure database access with proper network configuration
- Use Oracle Wallet for credential management