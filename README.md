# Interactive AI Holograms
Everything you need to know to build and run the Interactive AI Holograms exhibit...

See blog for details
[here](https://www.linkedin.com/pulse/interactive-ai-holograms-develop-digital-double-oracle-paul-parkinson-zdpjf)

## Example Questions

Try these to trigger specific agents (via the web UI text box, or by holding the Z key and speaking):

| Agent | Example question |
|---|---|
| Vision AI | "What do you see?" |
| Generate Image | "Generate image of a futuristic city skyline at sunset" |
| Edit Image (OpenAI) | "Edit image make it look like a painting" |
| Imagen Edit Image (GCP) | "vertex edit make it look like a watercolor" |
| Navy Ships | "Show me the ship USS Enterprise" |
| Navy Equipment | "Show me equipment for radar systems" |
| Mirror Me | "Mirror me" |
| Digital Twin | "Show the digital twin" |
| Sign | "Change the sign to say Welcome" |
| Clear History | "Clear history" |
| In-DB ONNX Vector RAG | "Search docs for Oracle database security best practices" |
| Spring AI Vector RAG | "Search documents about cloud architecture" |
| DB SQL (NL-to-SQL) | "Ask the database how many orders were placed last month" |
| DB Summarization | "Summarize the project requirements document" |
| DB Property Graph | "Who is connected to the engineering department?" |
| Spring AI Chat | "Chat with the database about inventory levels" |
| Langchain4j RAG | "Langchain search for deployment procedures" |
| Langchain4j Tool | "Langchain tool call to list database tables" |
| AI Toolkit | "Run the sandbox optimizer" |
| Financial | "Run the financial agent" |
| Gamer | "Run the gamer agent" |
| *(any other question)* | Falls through to the general-purpose LLM fallback agent |

## Run It

### Prerequisites

- Java 21 or newer
- A `.env` file or exported environment variables for your deployment (see configuration section below)
- The aiholo.jar file downloaded from [here](https://storage.googleapis.com/ai-holo/aiholo.jar)

### Set configuratoin and start the application

```bash
java -jar aiholo.jar
```

If your environment is set correctly, the app will start and serve the UI on:

```text
http://localhost:8082/aiholo
```

## Configuration

The JAR reads configuration from environment variables.

Minimum commonly needed settings:

```dotenv
SERVER_PORT=8082
AIHOLO_HOST_URL=http://localhost:8082

OPENAI_KEY=your-openai-api-key
OPENAI_API_KEY=your-openai-api-key
DB_USER=admin
DB_PASSWORD=your-database-password
DB_URL=jdbc:oracle:thin:@yourdb_high?TNS_ADMIN=/path/to/Wallet_yourdb
SERVER_PORT=8082
AIHOLO_HOST_URL=http://localhost:8082
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
$env:OPENAI_KEY="your-openai-api-key"
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
export OPENAI_KEY="your-openai-api-key"
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


This repos... 

![aiholo repos qr code](images/bit.ly_interactive-ai-holograms.png "this")

