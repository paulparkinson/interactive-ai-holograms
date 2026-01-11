# Oracle RAG AI Agent

Spring Boot REST API for Retrieval-Augmented Generation (RAG) using:
- **Oracle Database 23ai** for vector storage
- **Vertex AI Embeddings** (text-embedding-004) for vectorization
- **Vertex AI Gemini** (gemini-2.5-flash) for LLM response generation
- **LangChain4j** for orchestration

This service can be integrated with **Google Cloud Dialogflow conversational agents** as a custom tool/datastore.

## Features

- ✅ RESTful API with OpenAPI/Swagger documentation
- ✅ Oracle Database 23ai vector similarity search
- ✅ Vertex AI embeddings and Gemini LLM integration
- ✅ Production-ready Spring Boot architecture
- ✅ Health check endpoints
- ✅ Configurable via environment variables
- ✅ Ready for Dialogflow agent integration

## Prerequisites

- Java 17+
- Maven 3.8+
- Oracle Database 23ai with vector tables populated
- Oracle wallet files in `~/wallet/`
- Google Cloud credentials configured (`gcloud auth application-default login`)
- Access to Vertex AI APIs

## Quick Start

### 1. Configure Application

Edit `src/main/resources/application.yaml` or set environment variables:

```bash
# Oracle Database
export ORACLE_USERNAME=ADMIN
export ORACLE_PASSWORD=yourpassword
export ORACLE_DSN=paulparkdb_high
export ORACLE_WALLET_LOCATION=~/wallet
export ORACLE_WALLET_PASSWORD=YourWalletPassword123#

# Vertex AI
export VERTEX_PROJECT_ID=adb-pm-prod
export VERTEX_LOCATION=us-central1
```

### 2. Build and Run

```bash
# Build
mvn clean package

# Run
java -jar target/rag-ai-agent-1.0.0.jar

# Or using Maven
mvn spring-boot:run
```

The application will start on `http://localhost:8080`

### 3. Test the API

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

### 4. View API Documentation

Open your browser to:
- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **OpenAPI JSON**: http://localhost:8080/api-docs

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

## Dialogflow Integration

### 1. Deploy on GCP VM

On your GCP VM (34.48.146.146):

```bash
# Copy project to VM
scp -r rag-ai-agent/ user@34.48.146.146:~/

# SSH into VM
ssh user@34.48.146.146

# Build and run
cd rag-ai-agent
mvn clean package
nohup java -jar target/rag-ai-agent-1.0.0.jar > app.log 2>&1 &
```

### 2. Open Firewall

```bash
gcloud compute firewall-rules create allow-rag-api \
  --allow tcp:8080 \
  --source-ranges 0.0.0.0/0 \
  --target-tags rag-server

gcloud compute instances add-tags YOUR_VM_NAME \
  --zone us-east4-a \
  --tags rag-server
```

### 3. Configure Dialogflow Agent

In Dialogflow Agent Builder:

1. Go to **Tools** → **Create Tool**
2. Select **OpenAPI** type
3. Configure:
   - **Tool Name**: `Oracle Database Knowledge`
   - **Description**: `Use this tool to answer questions about Oracle Database 23ai features from technical documentation`
   - **OpenAPI Spec URL**: `http://34.48.146.146:8080/api-docs`
   
   Or paste this OpenAPI spec:

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
      summary: Query the RAG system
      operationId: queryRag
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

4. In your agent's **Playbook Instructions**, add:
```
- For questions about Oracle Database 23ai, JSON Relational Duality, or technical features, use ${TOOL: Oracle Database Knowledge}
```

### 4. Test Integration

In the Dialogflow simulator, ask:
- "Tell me about JSON Relational Duality"
- "What are the new features in Oracle 23ai?"
- "Explain Oracle AI Vector Search"

The agent will call your RAG API and return responses grounded in your PDF documents!

## Configuration

All settings can be configured via `application.yaml` or environment variables:

| Property | Environment Variable | Default | Description |
|----------|---------------------|---------|-------------|
| `oracle.database.username` | `ORACLE_USERNAME` | ADMIN | Oracle DB username |
| `oracle.database.password` | `ORACLE_PASSWORD` | - | Oracle DB password |
| `oracle.database.dsn` | `ORACLE_DSN` | paulparkdb_high | TNS connection string |
| `oracle.database.wallet-location` | `ORACLE_WALLET_LOCATION` | ~/wallet | Path to wallet directory |
| `oracle.database.wallet-password` | `ORACLE_WALLET_PASSWORD` | - | Wallet password |
| `vertex-ai.project-id` | `VERTEX_PROJECT_ID` | adb-pm-prod | GCP project ID |
| `vertex-ai.location` | `VERTEX_LOCATION` | us-central1 | Vertex AI region |
| `vertex-ai.llm-model` | `VERTEX_LLM_MODEL` | gemini-2.5-flash | Gemini model name |

## Architecture

```
┌─────────────────┐
│  Dialogflow     │
│  Agent          │
└────────┬────────┘
         │ HTTP POST /api/v1/query
         │
┌────────▼────────────────────────────┐
│  Spring Boot RAG Service            │
│  ┌──────────────────────────────┐   │
│  │  RagController               │   │
│  └──────────┬───────────────────┘   │
│             │                        │
│  ┌──────────▼───────────────────┐   │
│  │  RagService                  │   │
│  │  1. Generate question embed  │   │
│  │  2. Search Oracle vectors    │   │
│  │  3. Retrieve context chunks  │   │
│  │  4. Generate LLM response    │   │
│  └──────────┬───────────────────┘   │
│             │                        │
│  ┌──────────▼───────────────────┐   │
│  │  VertexAiService             │   │
│  │  - Embeddings API            │   │
│  │  - Gemini LLM API            │   │
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

# Verify TNS_ADMIN
echo $TNS_ADMIN

# Test connection
sqlplus ADMIN/password@paulparkdb_high
```

### Vertex AI Authentication
```bash
# Login
gcloud auth application-default login

# Verify credentials
gcloud auth application-default print-access-token

# Check project
gcloud config get-value project
```

### Port Already in Use
```bash
# Find process using port 8080
lsof -i :8080

# Kill process
kill -9 <PID>

# Or change port in application.yaml
server.port: 8081
```

## Production Deployment

For production, consider:

1. **Use HTTPS**: Add nginx reverse proxy or deploy to Cloud Run
2. **Authentication**: Add OAuth2/API keys to secure endpoints
3. **Connection Pooling**: Tune Oracle connection pool settings
4. **Monitoring**: Add Spring Boot Actuator and metrics
5. **Caching**: Cache frequent queries with Redis
6. **Rate Limiting**: Protect against abuse

## License

Apache 2.0

## Support

For issues or questions, contact the Oracle AI team.
