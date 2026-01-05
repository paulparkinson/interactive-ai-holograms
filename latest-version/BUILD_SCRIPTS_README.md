# Unified Build and Run Scripts

The `build_and_run_example.sh` and `build_and_run_example.ps1` scripts now support both standard and voice assistant modes.

## Usage

### Windows PowerShell

**Standard mode (no voice assistant):**
```powershell
cd latest-version
powershell -ExecutionPolicy Bypass -File .\build_and_run_example.ps1
```

**Voice assistant mode:**
```powershell
cd latest-version
$env:PORCUPINE_ACCESS_KEY = "your-key"
$env:KEYWORD_PATH = "C:\path\to\Hey-computer.ppn"
$env:OPENAI_API_KEY = "your-openai-key"

powershell -ExecutionPolicy Bypass -File .\build_and_run_example.ps1 -Voice
```

### Linux/macOS

**Standard mode (no voice assistant):**
```bash
cd latest-version
./build_and_run_example.sh
```

**Voice assistant mode:**
```bash
cd latest-version
export PORCUPINE_ACCESS_KEY="your-key"
export KEYWORD_PATH="/path/to/Hey-computer.ppn"
export OPENAI_API_KEY="your-openai-key"

./build_and_run_example.sh --voice
```

## What's Included

Both scripts now include:

1. **Standard Application Configuration**
   - All existing Oracle AI settings
   - Database, API, and service configuration
   - Port and server settings

2. **Optional Voice Assistant Configuration**
   - Porcupine wake word detection
   - Google Cloud Speech-to-Text
   - Language detection
   - ChatGPT integration
   - Text-to-Speech playback

## Switching Modes

- **Without flag**: Runs standard application
- **With `-Voice` (PowerShell) or `--voice` (bash)**: Enables voice assistant

## Environment Variables

### Always Available
- `SERVER_PORT` - Server port (default: 8080)
- `AIHOLO_HOST_URL` - Application URL
- `OPENAI_API_KEY` - ChatGPT API key

### Voice Assistant Only (when `-Voice` / `--voice` is used)
- `PORCUPINE_ACCESS_KEY` - Picovoice access key
- `KEYWORD_PATH` - Path to wake word model file
- `ENABLE_LANGUAGE_DETECTION` - Auto-detect language (default: true)
- `RESPONSE_LANGUAGE` - Response language mode (default: both)

## Note

The individual `build_and_run_with_voice.sh` and `build_and_run_with_voice.ps1` scripts are still available if you prefer them, but the unified `build_and_run_example.*` scripts are now the recommended approach.
