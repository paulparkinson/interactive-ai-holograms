# Oracle RAG AI Agent - Python Flask

Flask REST API for Retrieval-Augmented Generation (RAG) using:
- **Oracle Database 23ai** for vector storage
- **Vertex AI Embeddings** (text-embedding-004) for vectorization
- **Vertex AI Gemini** (gemini-2.5-flash) for LLM response generation
- **LangChain** for orchestration

This service can be integrated with **Google Cloud Dialogflow conversational agents** as a custom tool/datastore.

## Features

- ✅ RESTful API with OpenAPI/Swagger support
- ✅ Oracle Database 23ai vector similarity search
- ✅ Vertex AI embeddings and Gemini LLM integration
- ✅ Production-ready Flask architecture
- ✅ Environment-based configuration
- ✅ Health check endpoints
- ✅ Ready for Dialogflow agent integration

## Prerequisites

- Python 3.9+
- Oracle Database 23ai with vector tables populated
- Oracle wallet files in `~/wallet/`
- Google Cloud credentials configured (`gcloud auth application-default login`)
- Access to Vertex AI APIs

## Quick Start

### 1. Install Dependencies

```bash
cd oracle-ai-database-gcp-vertex-ai/rag-ai-agent-python

# Create virtual environment (recommended)
python -m venv venv
source venv/bin/activate  # On Windows: venv\Scripts\activate

# Install dependencies
pip install -r requirements.txt
```

### 2. Configure Environment

Copy `.env.example` to `.env` and update with your values:

```bash
cp .env.example .env
# Edit .env with your Oracle DB and Vertex AI credentials
```

### 3. Run the Application

```bash
# Development mode
python app.py

# Production mode with gunicorn
gunicorn -w 4 -b 0.0.0.0:8080 app:app
```

The application will start on `http://localhost:8080`

### 4. Test the API

**Using curl:**
```bash
curl -X POST http://localhost:8080/api/v1/query \
  -H "Content-Type: application/json" \
  -d '{"question": "Tell me more about JSON Relational Duality"}'
```

**Response:**
```json
{
  "answer": "JSON Relational Duality in Oracle Database 23ai allows you to...",
  "source": "Oracle Database 23ai Vector RAG",
  "responseTimeMs": 1250
}
```

**Health check:**
```bash
curl http://localhost:8080/api/v1/health
```

## API Endpoints

### POST /api/v1/query
Query the RAG system with a question.

**Request:**
```json
{
  "question": "Tell me about Oracle AI Vector Search"
}
```

**Response:**
```json
{
  "answer": "Oracle AI Vector Search enables...",
  "source": "Oracle Database 23ai Vector RAG",
  "responseTimeMs": 1250
}
```

### GET /api/v1/health
Health check endpoint.

**Response:**
```json
{
  "status": "UP",
  "timestamp": "2026-01-09T10:30:00Z"
}
```

### GET /api-docs
OpenAPI specification endpoint.

Returns the OpenAPI 3.0 specification for the API.

## Deployment

### Deploy on GCP VM

```bash
# 1. Copy files to VM
scp -r rag-ai-agent-python/ user@34.48.146.146:~/

# 2. SSH into VM
ssh user@34.48.146.146

# 3. Install dependencies
cd rag-ai-agent-python
pip install -r requirements.txt

# 4. Configure environment
cp .env.example .env
# Edit .env with your credentials

# 5. Run with gunicorn
nohup gunicorn -w 4 -b 0.0.0.0:8080 app:app > app.log 2>&1 &
```

### Open Firewall

```bash
gcloud compute firewall-rules create allow-rag-api \
  --allow tcp:8080 \
  --source-ranges 0.0.0.0/0 \
  --target-tags rag-server

gcloud compute instances add-tags YOUR_VM_NAME \
  --zone us-east4-a \
  --tags rag-server
```

## Dialogflow Integration

### OpenAPI Spec for Dialogflow

Use this OpenAPI spec when creating a custom tool in Dialogflow:

```yaml
openapi: 3.0.0
info:
  title: Oracle RAG AI Agent API
  version: 1.0.0
servers:
  - url: http://34.48.146.146:8080
paths:
  /api/v1/query:
    post:
      summary: Query Oracle Database knowledge
      operationId: queryOracleKnowledge
      requestBody:
        required: true
        content:
          application/json:
            schema:
              type: object
              properties:
                question:
                  type: string
      responses:
        '200':
          description: Successful response
          content:
            application/json:
              schema:
                type: object
                properties:
                  answer:
                    type: string
```

### Configure Dialogflow Agent

1. In Dialogflow Agent Builder, create a new tool
2. Select **OpenAPI** type
3. Tool name: `Oracle Database Knowledge`
4. Description: `Use this tool to answer questions about Oracle Database 23ai features`
5. Paste the OpenAPI spec above or use: `http://34.48.146.146:8080/api-docs`

6. Update agent instructions:
```
- For questions about Oracle Database 23ai, JSON Relational Duality, or technical features, use ${TOOL: Oracle Database Knowledge}
```

## Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `ORACLE_USERNAME` | Oracle DB username | ADMIN |
| `ORACLE_PASSWORD` | Oracle DB password | - |
| `ORACLE_DSN` | TNS connection string | paulparkdb_high |
| `ORACLE_WALLET_LOCATION` | Path to wallet directory | ~/wallet |
| `ORACLE_WALLET_PASSWORD` | Wallet password | - |
| `ORACLE_VECTOR_TABLE` | Vector table name | RAG_TAB |
| `VERTEX_PROJECT_ID` | GCP project ID | adb-pm-prod |
| `VERTEX_LOCATION` | Vertex AI region | us-central1 |
| `VERTEX_EMBEDDING_MODEL` | Embedding model | text-embedding-004 |
| `VERTEX_LLM_MODEL` | Gemini model | gemini-2.5-flash |
| `RAG_TOP_K` | Number of chunks to retrieve | 10 |
| `PORT` | Server port | 8080 |

## Architecture

```
┌─────────────────┐
│  Dialogflow     │
│  Agent          │
└────────┬────────┘
         │ HTTP POST /api/v1/query
         │
┌────────▼────────────────────────────┐
│  Flask RAG Service                  │
│  ┌──────────────────────────────┐   │
│  │  /api/v1/query endpoint      │   │
│  └──────────┬───────────────────┘   │
│             │                        │
│  ┌──────────▼───────────────────┐   │
│  │  execute_rag_query()         │   │
│  │  1. Connect to Oracle DB     │   │
│  │  2. Initialize vector store  │   │
│  │  3. Build RAG chain          │   │
│  │  4. Execute retrieval        │   │
│  │  5. Generate LLM response    │   │
│  └──────────┬───────────────────┘   │
│             │                        │
│  ┌──────────▼───────────────────┐   │
│  │  LangChain Components        │   │
│  │  - VertexAIEmbeddings        │   │
│  │  - ChatVertexAI (Gemini)     │   │
│  │  - OracleVS (Vector Store)   │   │
│  └──────────────────────────────┘   │
└─────────────┬───────────────────────┘
              │
    ┌─────────▼──────────┐
    │  Oracle Database   │
    │  23ai Vector Store │
    │  (RAG_TAB)         │
    └────────────────────┘
```

## Troubleshooting

### Database Connection Issues
```bash
# Check wallet files
ls -la ~/wallet/

# Test connection
python -c "import oracledb; print('oracledb version:', oracledb.__version__)"
```

### Vertex AI Authentication
```bash
# Login
gcloud auth application-default login

# Verify
gcloud auth application-default print-access-token
```

### Port Already in Use
```bash
# Find process
lsof -i :8080  # Linux/Mac
netstat -ano | findstr :8080  # Windows

# Change port in .env
PORT=8081
```

## Development

```bash
# Install dev dependencies
pip install -r requirements.txt

# Run in debug mode
export FLASK_ENV=development
python app.py
```

## Production Deployment

For production:
1. **Use HTTPS**: Add nginx reverse proxy or deploy to Cloud Run
2. **Authentication**: Add API key authentication
3. **Monitoring**: Add logging and metrics
4. **Caching**: Cache frequent queries
5. **Rate Limiting**: Protect against abuse

## License

Apache 2.0
