# Interactive AI Holograms
Everything you need to know to build and run the Interactive AI Holograms exhibit...

See blog for details
[here](https://www.linkedin.com/pulse/interactive-ai-holograms-develop-digital-double-oracle-paul-parkinson-zdpjf)

## Example Questions

Try these to trigger specific agents (via the web UI text box, or by holding the Z key and speaking):

| Agent | Example question |
|---|---|
| LLM only (the default) | "What is a vector search?"|
| Spring AI Vector RAG | "Search documents about Oracle database features" |
| In-DB ONNX Vector RAG | "Search docs for Oracle database security best practices" |
| Mirror Me | "Mirror me" |
| Financial | "Describe my stock portfolio, use financial agent" |
| Clear History | "Clear history" |

## Build And Run It

### Prerequisites

- Database, Speech AI, and LLM Setup as described in the next section below
- Java 21 or newer
- The aiholo.jar file downloaded from [here](https://storage.googleapis.com/ai-holo/aiholo.jar)
- An `.env` file or exported environment variables for your deployment (see configuration section below and .env_example)

## Database, Speech AI, and LLM Setup

### Oracle Database with Vector Support

Run Oracle 26ai Free with native VECTOR support using Docker:

```bash
docker run -d \
  --name oracle-free \
  --restart unless-stopped \
  -p 1521:1521 \
  -e ORACLE_PWD=<your-password> \
  -v /path/to/data/oracle:/opt/oracle/oradata \
  --cpus="8.0" \
  --memory="64g" \
  container-registry.oracle.com/database/free:latest
```

**Connection details:**
- Host: `localhost` (or your server IP for remote access)
- Port: `1521`
- Service: `FREEPDB1`
- User: `SYSTEM` or `PDBADMIN`
- Password: `<your-password>`

**Note:** For a complete development stack with Python FastAPI + Spring Boot + Oracle Vector Search, see the [oracleaidatabase-devenv-util](oracleaidatabase-devenv-util/) folder.

### Ollama (Local LLM)

Install Ollama and pull recommended models:

```bash
# Install Ollama
curl -fsSL https://ollama.com/install.sh | sh

# Pull recommended models
ollama pull qwen2.5:7b    # Best overall
ollama pull llama3.2      # Fast general-purpose model (3B params)
ollama pull mistral       # Strong reasoning model (7B params)
ollama pull nomic-embed-text  # Text embedding model for RAG

# Verify installation
ollama list

# test model with ollama or via API...
ollama run llama3.2

curl http://localhost:11434/api/generate -d '{
  "model": "llama3.2",
  "prompt": "Say hello in one sentence."
}'
```

**API endpoint:** `http://localhost:11434`

**To accept requests from other machines** (required for database integration), set the host to listen on all interfaces:

```bash
# Set environment variable
export OLLAMA_HOST=0.0.0.0:11434

# Restart Ollama service
systemctl restart ollama  # On Linux with systemd
# or
pkill ollama && ollama serve  # Manual restart

# Verify it's listening on all interfaces
netstat -tlnp | grep 11434
```

For in-database Ollama integration with Oracle, ensure Ollama is accessible from the database server and configure using `DBMS_CLOUD_AI` or `DBMS_VECTOR_CHAIN`.

### Google Cloud Speech AI

Install and configure Google Cloud SDK for speech-to-text and text-to-speech:

```bash
# Install gcloud CLI
curl https://sdk.cloud.google.com | bash
exec -l $SHELL

# Initialize and authenticate
gcloud init
gcloud auth application-default login

# Enable required APIs
gcloud services enable speech.googleapis.com
gcloud services enable texttospeech.googleapis.com

# Set project (replace with your project ID)
gcloud config set project YOUR_PROJECT_ID
```

**Required environment variables:**
```bash
export GOOGLE_APPLICATION_CREDENTIALS=/path/to/service-account-key.json
export GOOGLE_CLOUD_PROJECT=your-project-id
```

### Local/Offline Speech AI (no cloud required)

For fully offline STT and TTS, use Whisper and Piper instead of Google Cloud.

**Whisper STT** — local speech-to-text via an OpenAI-compatible API server:

```bash
# Install faster-whisper-server (Python)
pip install faster-whisper-server

# Start the server
faster-whisper-server --host 0.0.0.0 --port 8000
```

```dotenv
STT_ENGINE=WHISPER
WHISPER_URL=http://localhost:8000
WHISPER_MODEL=base
```

Models are downloaded automatically on first use. Larger models (`small`, `medium`, `large-v3`) are more accurate but slower.

**Piper TTS** — fast local text-to-speech using ONNX models:

1. Download Piper from [GitHub releases](https://github.com/rhasspy/piper/releases)
2. Download a voice model from [HuggingFace](https://huggingface.co/rhasspy/piper-voices) (`.onnx` + `.onnx.json` files)

```dotenv
TTS_ENGINE=PIPER
PIPER_EXE_PATH=/path/to/piper
PIPER_MODEL_PATH=/path/to/en_US-kathleen-low.onnx
```

**Fully offline stack** — complete `.env` snippet for zero-internet operation:

```dotenv
# LLM — local Ollama
DEFAULT_LLM_PROVIDER=ollama
OLLAMA_URL=http://localhost:11434
OLLAMA_MODEL=llama3.2:3b

# STT — local Whisper
STT_ENGINE=WHISPER
WHISPER_URL=http://localhost:8000
WHISPER_MODEL=base

# TTS — local Piper
TTS_ENGINE=PIPER
PIPER_EXE_PATH=/path/to/piper
PIPER_MODEL_PATH=/path/to/en_US-kathleen-low.onnx
```

No API keys, no cloud accounts, no internet required. Combine with Oracle Database for in-DB vector RAG to complete the stack.

> **Note:** Piper is recommended over Coqui for local TTS. Coqui TTS (the company) shut down in 2023 and the open-source project is no longer maintained. Piper is actively developed, faster on CPU, has more voice models, and requires no Python runtime.

### Set Configuration and Start the Application

```bash
java -jar aiholo.jar
```

If your environment is set correctly, the app will start and serve the UI on:

```text
http://localhost:8082/aiholo
```

## Input Methods

There are two ways to send questions to the application. Both go through the same `AgentService.processQuestion()` pipeline, so `ENABLED_AGENTS`, `AGENT_ROUTING_MODE`, and all other agent settings apply equally to both.

| Input method | How it works | Config |
|---|---|---|
| **Web UI** | Browser-based interface at `/aiholo` — type a question or click the microphone to record audio | Always available |
| **Hotkey / Voice Assistant** | System-wide hotkeys (Z = speak, X = speak + webcam, A hold = stop audio) and optional wake-word detection (Porcupine or OpenWakeWord) | `ENABLE_GLOBAL_HOTKEY`, `ENABLE_VOICE_ASSISTANT` |

## Configuration

The JAR reads configuration from environment variables.

Minimum commonly needed settings:

```dotenv
SERVER_PORT=8082
AIHOLO_HOST_URL=http://localhost:8082

# LLM provider: openai, claude, ollama, gemini
DEFAULT_LLM_PROVIDER=openai
OPENAI_API_KEY=your-openai-api-key

# For local/offline LLM instead:
# DEFAULT_LLM_PROVIDER=ollama
# OLLAMA_URL=http://localhost:11434
# OLLAMA_MODEL=mistral:latest

DB_USER=admin
DB_PASSWORD=your-database-password
DB_URL=jdbc:oracle:thin:@yourdb_high?TNS_ADMIN=/path/to/Wallet_yourdb
OUTPUT_FILE_PATH=/path/to/aiholo_output.txt
```

If you are using voice assistant features, also set:

```dotenv
ENABLE_VOICE_ASSISTANT=true
VOICE_ASSISTANT_ENGINE=porcupine
PORCUPINE_ACCESS_KEY=your-porcupine-key
KEYWORD_PATH=/path/to/Hey-computer.ppn
```

Or with OpenWakeWord:

```dotenv
ENABLE_VOICE_ASSISTANT=true
VOICE_ASSISTANT_ENGINE=openwakeword
OPENWAKEWORD_SCRIPT_PATH=wakeupwords/openwakeword_bridge.py
OPENWAKEWORD_MODEL=hey_jarvis
```

## Notes

- The packaged artifact name is `aiholo.jar`
- Run from the repo root, or adjust the path to the JAR accordingly
- Keep `SERVER_PORT` and `AIHOLO_HOST_URL` aligned
- Default local credentials are `oracleai / oracleai`

## Example

PowerShell:

```powershell
$env:OPENAI_API_KEY="your-openai-api-key"
$env:DB_USER="admin"
$env:DB_PASSWORD="your-database-password"
$env:DB_URL="jdbc:oracle:thin:@yourdb_high?TNS_ADMIN=C:\path\to\Wallet_yourdb"
$env:SERVER_PORT="8082"
$env:AIHOLO_HOST_URL="http://localhost:8082"
java -jar target/aiholo.jar
```

Bash:

```bash
export OPENAI_API_KEY="your-openai-api-key"
export DB_USER="admin"
export DB_PASSWORD="your-database-password"
export DB_URL="jdbc:oracle:thin:@yourdb_high?TNS_ADMIN=/path/to/Wallet_yourdb"
export SERVER_PORT="8082"
export AIHOLO_HOST_URL="http://localhost:8082"
java -jar target/aiholo.jar
```

### `ENABLED_AGENTS`

`ENABLED_AGENTS` is a comma-separated list of agent `valueName`s.

Examples:

```dotenv
ENABLED_AGENTS=visionagent,shipsagent
```

```dotenv
ENABLED_AGENTS=visionagent,shipsagent,equipmentagent
```

Behavior:

- **`ENABLED_AGENTS` should be set in your `.env` file** — it controls which agents are active
- Only agents whose `valueName` appears in the list will load
- The fallback path (`DirectLLMAgent` / `DefaultFallbackAgent`) is always registered regardless
- Custom `@Component` agents are also discovered and filtered by their `valueName`
- This filter applies to **all input methods** — both the web UI and the hotkey/voice assistant go through `AgentService.processQuestion()`, so the same set of agents is available everywhere

### Built-in agent values

The sample env documents these values:

| `ENABLED_AGENTS` value | Purpose |
|---|---|
| `clearhistory` | Clears conversation history |
| `mirrormeagent` | Mirror/digital double behavior |
| `shipsagent` | Navy ship lookup |
| `equipmentagent` | Navy equipment lookup |
| `digitaltwinagent` | Digital twin actions |
| `signagent` | Sign/display output actions |
| `visionagent` | Vision/image analysis |
| `aitoolkitagent` | Sandbox/toolkit integration |
| `financialagent` | Financial flow integration |
| `gameragent` | Game-oriented routing |
| `indbonnxvectorrag` | In-DB ONNX vector RAG via Oracle SQL function |
| `image` | Generate images from text via OpenAI DALL-E |
| `editimage` | Capture webcam photo, modify it per user instruction via GPT-4o + DALL-E |
| `imageneditimage` | Capture webcam photo, edit the actual image via Google Vertex AI Imagen |
| `springaivectorrag` | Spring AI VectorStore RAG with OpenAI embeddings |
| `dbsqlagent` | Natural language to SQL via DBMS_CLOUD_AI |
| `dbsummarizationagent` | In-database summarization via DBMS_VECTOR_CHAIN |
| `dbpropertygraphagent` | Property graph queries via SQL/PGQ |
| `springaichatagent` | Spring AI ChatClient with Oracle DB grounding |
| `langchain4joraclerag` | Langchain4j OracleEmbeddingStore RAG |
| `langchain4jtoolagent` | Langchain4j tool/function-calling with Oracle DB |
| `generalagent` | Fallback routing value used by the built-in fallback agents |

Notes:

- Both `DirectLLMAgent` and `DefaultFallbackAgent` currently use `generalagent` as their `valueName`
- Built-in agents register before auto-discovered custom agents, so built-ins win when trigger keywords overlap

### Agent Routing Mode

By default, questions are routed to agents via keyword matching (`getKeywords()`). You can optionally let the configured LLM pick the best agent instead:

```dotenv
# keyword = fast, deterministic keyword matching (default)
# llm     = LLM reads each agent's description and picks the best match
AGENT_ROUTING_MODE=keyword
```

When `AGENT_ROUTING_MODE=llm`, the system builds a prompt listing all registered agents with their `getAgentDescription()` text and asks the LLM to return the index of the best match. If the LLM returns "none" or fails, it falls back to keyword matching automatically.

**Trade-offs:**
- `keyword` — instant, no extra LLM call, deterministic, requires users to use trigger phrases
- `llm` — handles natural phrasing ("can you look at that ISO doc?" routes to the RAG agent), but adds one LLM round-trip per question

## Default LLM Provider

The fallback/default agent supports multiple LLM providers, controlled by a single env var:

| Provider | `DEFAULT_LLM_PROVIDER` | API Key | Model env var | Default model |
|---|---|---|---|---|
| OpenAI | `openai` (default) | `OPENAI_API_KEY` | `OPENAI_MODEL` | `gpt-4` |
| Ollama (local) | `ollama` | None needed | `OLLAMA_MODEL` | `mistral:latest` |
| Claude | `claude` | `CLAUDE_API_KEY` | `CLAUDE_MODEL` | `claude-sonnet-4-20250514` |
| Gemini | `gemini` | `GEMINI_API_KEY` | `GEMINI_MODEL` | `gemini-2.0-flash` |

For fully offline operation, use Ollama:

```dotenv
DEFAULT_LLM_PROVIDER=ollama
OLLAMA_URL=http://localhost:11434
OLLAMA_MODEL=llama3.2:3b
```

The provider is set at startup. The `LLMService` handles all REST API differences internally — agents just call `llmService.query(prompt)`.

## Database-Backed Agent Summary

The following agents use Oracle Database 23ai. The key axis is **where inference runs** (in-DB vs external) and **which framework manages the interaction** (raw JDBC, Spring AI, Langchain4j).

> \* **SpringAIVectorRAGAgent is the most straightforward starting point** — it uses standard Spring AI APIs with OpenAI embeddings and requires minimal database-side setup. The examples below are included in the main library/distribution and are meant to be customized for your use case.

| Agent Name | Description | Framework | LLM Location | DB Role | DB Prep Required | `valueName` |
|---|---|---|---|---|---|---|
| InDBOnnxVectorRAGAgent | Full in-database RAG: ONNX embeddings + Ollama or other LLM, all via a single SQL function call | JdbcTemplate | In-database (Ollama) | RAG + inference | Run `setup_onnx_vector_rag.sql` (creates ONNX model, vector table, and SQL function) | `indbonnxvectorrag` |
| SpringAIVectorRAGAgent \* | Spring AI vector similarity search with OpenAI embeddings stored in Oracle | Spring AI | External (OpenAI) | Vector store only | Table auto-created by Spring AI on startup | `springaivectorrag` |
| DBSQLAgent | Natural language to SQL — asks questions in English, gets answers from relational data | JdbcTemplate | In-database (DBMS_CLOUD_AI) | NL-to-SQL | Run `setup_dbms_cloud_ai.sql` (configures AI profile and credentials) | `dbsqlagent` |
| DBSummarizationAgent | In-database document summarization without leaving Oracle | JdbcTemplate | In-database (DBMS_VECTOR_CHAIN) | Summarization | Run `setup_vector_chain.sql` (configures LLM credential for DBMS_VECTOR_CHAIN) | `dbsummarizationagent` |
| DBPropertyGraphAgent | Relationship-based queries using SQL/PGQ graph pattern matching | JdbcTemplate | N/A (SQL/PGQ) | Graph queries | Run `setup_property_graph.sql` (CREATE PROPERTY GRAPH over your tables) | `dbpropertygraphagent` |
| SpringAIChatAgent | Spring AI ChatClient with optional Oracle DB context for grounded responses | Spring AI | External (OpenAI) | Tool/grounding backend | None (uses existing tables for optional context) | `springaichatagent` |
| Langchain4jOracleRAGAgent | Langchain4j vector search using OracleEmbeddingStore | Langchain4j | External (any) | Vector store (OracleEmbeddingStore) | Table auto-created by Langchain4j `OracleEmbeddingStore.builder()` | `langchain4joraclerag` |
| Langchain4jToolAgent | Langchain4j tool/function-calling pattern backed by Oracle DB queries | Langchain4j | N/A | Tool/function backend | None (queries existing schema metadata) | `langchain4jtoolagent` |

All database-backed agents share a single `DataSource` configured via `DataSourceConfiguration` (using `DB_USER`, `DB_PASSWORD`, `DB_URL` environment variables). Currently only one shared database can be specified; however, agents can create additional `DataSource` instances programmatically if they need to connect to a different database.

### Vector Store Table

The `SpringAIVectorRAGAgent` uses `OracleDBVectorStore`, which manages the vector store table in Oracle Database. The table is **auto-created at startup** if it does not already exist. The schema is:

| Column | Type | Description |
|---|---|---|
| `id` | `NUMBER GENERATED AS IDENTITY` (PK) | Auto-generated row ID |
| `text` | `CLOB` | The document text chunk |
| `embeddings` | `VECTOR` | OpenAI embedding vector (stored using Oracle 23ai native VECTOR type) |
| `metadata` | `JSON` | Document metadata (source file, page number, etc.) |

PDFs are uploaded via the `/vectorrag` UI, split into chunks by `TokenTextSplitter`, embedded using OpenAI's `text-embedding-ada-002`, and inserted into the table. Similarity search uses Oracle's built-in vector distance functions (`COSINE_DISTANCE`, `L2_DISTANCE`, etc.) with no external vector database required.

The table name, distance metric, and startup behavior are configured in `application.yaml`:

```yaml
vectorrag:
  table-name: ${VECTORRAG_TABLE_NAME:vector_store}
  drop-at-startup: ${VECTORRAG_DROP_AT_STARTUP:false}
  distance-metric: ${VECTORRAG_DISTANCE_METRIC:COSINE}
  temp-dir: ${VECTORRAG_TEMP_DIR:tempDir}
```

These can also be overridden via environment variables (`VECTORRAG_TABLE_NAME`, `VECTORRAG_DISTANCE_METRIC`, etc.). The default table name is `vector_store` and the default distance metric is `COSINE`.

## Adding a Custom Agent

Custom agents are auto-discovered if they:

- implement [`Agent`](src/main/java/oracleai/aiholo/agents/Agent.java)
- are on the application classpath (place the `.java` file under `src/main/java/oracleai/aiholo/agents/` in the source tree, or add the compiled `.class`/`.jar` to the classpath via `-cp` or by dropping it into the Spring Boot loader's `BOOT-INF/classes/` directory)
- are annotated with `@Component`

Minimal example:

```java
package oracleai.aiholo.agents;

import org.springframework.stereotype.Component;

@Component
public class WeatherAgent implements Agent {

    @Override
    public String getName() {
        return "Weather Agent";
    }

    @Override
    public String getValueName() {
        return "weatheragent";
    }

    @Override
    public String getAgentDescription() {
        return "Answers questions about current weather, forecasts, and temperature.";
    }

    @Override
    public String[][] getKeywords() {
        return new String[][] {
            {"weather"},
            {"forecast"},
            {"temperature"}
        };
    }

    @Override
    public String processQuestion(String question) {
        return "Weather agent received: " + question;
    }

    @Override
    public boolean isConfigured() {
        return true;
    }
}
```

### How discovery works

At startup, `AgentService`:

1. registers built-in agents
2. registers fallback agents
3. scans the Spring application context for additional `Agent` beans
4. skips any duplicate `valueName`

### Making a custom agent selectable

If you want your custom agent to participate in `ENABLED_AGENTS`, set a stable `valueName` and use that exact value in `.env`:

```dotenv
ENABLED_AGENTS=weatheragent
```

### Design rules that matter

- `getValueName()` should be lowercase and stable
- `getKeywords()` is simple keyword-set matching, not semantic routing
- Empty `getKeywords()` makes an agent behave like a fallback
- Use unique keywords if you do not want a built-in agent to match first
- Return `false` from `isConfigured()` when required credentials or dependencies are missing

Contact Paul Parkinson with any questions or recommendations.


![aiholo repos qr code](images/bit.ly_interactive-ai-holograms.png "Interactive AI Holograms")

