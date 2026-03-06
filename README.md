# Interactive AI Holograms
Everything you need to know to build and run the Interactive AI Holograms exhibit...

See blog for details
[here](https://www.linkedin.com/pulse/interactive-ai-holograms-develop-digital-double-oracle-paul-parkinson-zdpjf)

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

- If `ENABLED_AGENTS` is unset or empty, all configured agents load
- If `ENABLED_AGENTS` is set, only matching agents load
- The fallback path is always registered with `alwaysLoad=true`
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
| `ollamadbrag` | Ollama-based RAG via Oracle DB SQL function |
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

## Adding a Custom Agent

Custom agents are auto-discovered if they:

- implement [`Agent`](latest-version/src/main/java/oracleai/aiholo/agents/Agent.java)
- are on the application classpath
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



## Database-Backed Agent Summary

The following agents use Oracle Database 23ai. The key axis is **where inference runs** (in-DB vs external) and **which framework manages the interaction** (raw JDBC, Spring AI, Langchain4j).

| Agent Name | Framework | LLM Location | DB Role | `valueName` |
|---|---|---|---|---|
| OllamaDBRAGAgent | JdbcTemplate | In-database (Ollama) | RAG + inference | `ollamadbrag` |
| SpringAIVectorRAGAgent | Spring AI | External (OpenAI) | Vector store only | `springaivectorrag` |
| DBSQLAgent | JdbcTemplate | In-database (DBMS_CLOUD_AI) | NL-to-SQL | `dbsqlagent` |
| DBSummarizationAgent | JdbcTemplate | In-database (DBMS_VECTOR_CHAIN) | Summarization | `dbsummarizationagent` |
| DBPropertyGraphAgent | JdbcTemplate | N/A (SQL/PGQ) | Graph queries | `dbpropertygraphagent` |
| SpringAIChatAgent | Spring AI | External (OpenAI) | Tool/grounding backend | `springaichatagent` |
| Langchain4jOracleRAGAgent | Langchain4j | External (any) | Vector store (OracleEmbeddingStore) | `langchain4joraclerag` |
| Langchain4jToolAgent | Langchain4j | N/A | Tool/function backend | `langchain4jtoolagent` |

**Non-database agents** (ShowNavyShips, VisionAI, Gamer, Financial, Sign, Mirror, etc.) use HTTP APIs, hardcoded data, or file I/O.

Contact Paul Parkinson with any questions or recommendations.


This repos... 

![aiholo repos qr code](images/bit.ly_interactive-ai-holograms.png "this")

