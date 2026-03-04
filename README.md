# Interactive AI Holograms
Everything you need to know to build and run the Interactive AI Holograms exhibit...

See blog for details
[here](https://www.linkedin.com/pulse/interactive-ai-holograms-develop-digital-double-oracle-paul-parkinson-zdpjf)

## Run It

### Prerequisites

- Java 21 or newer
- A `.env` file or exported environment variables for your deployment (see configuration section below)
- The aiholo.jar file downloaded from [here](https://www.linkedin.com/pulse/interactive-ai-holograms-develop-digital-double-oracle-paul-parkinson-zdpjf)

### Start the application

```bash
java -jar aiholo.jar
```

If your environment is set correctly, the app will start and serve the UI on:

```text
http://localhost:8082/aiholo
```

That assumes your environment sets:

```dotenv
SERVER_PORT=8082
AIHOLO_HOST_URL=http://localhost:8082
```

## Configuration

The JAR reads configuration from environment variables.

Minimum commonly needed settings:

```dotenv
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

Contact Paul Parkinson with any questions or recommendations.

This repos... 

![aiholo repos qr code](images/bit.ly_interactive-ai-holograms.png "this")

