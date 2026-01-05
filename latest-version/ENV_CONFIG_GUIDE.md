# Environment Configuration Guide

## Overview

All environment variables are now centralized in the `.env` file for easier configuration management. The PowerShell build script (`build_and_run_with_voice.ps1`) automatically loads these variables at startup.

## Quick Start

1. **Edit the `.env` file** with your configuration values
2. **Run the application** using `build_and_run_with_voice.bat` (recommended) or `build_and_run_with_voice.ps1`
3. The script will automatically load all environment variables from `.env`

## Configuration Sections

### OpenAI Configuration
```env
OPENAI_KEY=your-openai-api-key
OPENAI_API_KEY=your-openai-api-key
OPENAI_MODEL=gpt-4
```

### Database Configuration
```env
DB_USER=admin
DB_PASSWORD=your-password
DB_URL=jdbc:oracle:thin:@aiholodb_high?TNS_ADMIN=C:/path/to/Wallet_aiholodb
```

### TTS (Text-to-Speech) Configuration
```env
# TTS_ENGINE options: GCP, OCI, COQUI
TTS_ENGINE=COQUI

# TTS_QUALITY options for Coqui: FAST, BALANCED, QUALITY
TTS_QUALITY=QUALITY
```

### Audio Device Configuration
```env
# Stream A → Unreal Engine (Live Link Hub source)
AUDIO_DEVICE_A=CABLE Input (VB-Audio Virtual Cable)

# Stream B → Zoom/Speakers (local output)
AUDIO_DEVICE_B=Speakers (2- Axiim Link)
```

See [AUDIO_DEVICES.md](AUDIO_DEVICES.md) for available audio devices on your system.

### Voice Assistant Configuration
```env
ENABLE_VOICE_ASSISTANT=true
PORCUPINE_ACCESS_KEY=your-porcupine-access-key
KEYWORD_PATH=C:/path/to/Hey-computer_en_windows_v4_0_0.ppn
ENABLE_LANGUAGE_DETECTION=true
RESPONSE_LANGUAGE=same
```

### Langflow Configuration
```env
LANGFLOW_SERVER_URL=http://your-langflow-server:7860/api/
LANGFLOW_FLOW_ID=your-flow-id
LANGFLOW_API_KEY=your-langflow-api-key
```

### Path Configuration
```env
AIHOLO_HOST_URL=http://localhost:80
AUDIO_DIR_PATH=C:/path/to/audio-aiholo/
OUTPUT_FILE_PATH=C:/Users/youruser/aiholo_output.txt
```

### Java and Maven Paths
```env
JAVA_HOME=C:\Program Files\BellSoft\LibericaNIK-23-OpenJDK-21
MAVEN_HOME=C:\tools\apache-maven-3.9.6
```

## Optional Configuration

Some configurations are commented out in the `.env` file. To enable them, uncomment the lines:

### OCI Vision AI (Optional)
```env
# OCI_VISION_ENDPOINT=https://vision.aiservice.YOUR_REGION.oci.oraclecloud.com/20220125
# OCI_COMPARTMENT_ID=ocid1.compartment.oc1..YOUR_COMPARTMENT_ID
```

### Audio2Face Integration (Optional)
```env
# IS_AUDIO2FACE=true
```

### Remote API Poller (Optional)
```env
# REMOTE_API_URL=https://aiholo2.org/api/getValue
# REMOTE_API_USER=oracleai
# REMOTE_API_PASSWORD=oracleai
```

## Security Best Practices

1. **Never commit `.env` to version control** - Add `.env` to your `.gitignore` file
2. **Use strong passwords** - Especially for database and API keys
3. **Rotate API keys regularly** - Update keys in `.env` file when rotating
4. **Limit file permissions** - Ensure `.env` is readable only by your user account

## Troubleshooting

### Environment variables not loading
- Check that `.env` file is in the same directory as `build_and_run_with_voice.ps1`
- Ensure there are no syntax errors in `.env` (use `KEY=VALUE` format)
- Restart the application after modifying `.env`

### Missing required variables
If you see warnings about missing variables, check that all required fields in `.env` are filled in:
- `OPENAI_API_KEY` - Required for ChatGPT integration
- `DB_USER`, `DB_PASSWORD`, `DB_URL` - Required for database access
- `PORCUPINE_ACCESS_KEY` - Required for voice assistant wake word detection
- `KEYWORD_PATH` - Required for voice assistant wake word model

### Path issues
- Use forward slashes `/` or escaped backslashes `\\` in paths
- Ensure paths exist on your system before running
- Use absolute paths for clarity

## Migration from PowerShell Script

The old approach had environment variables hardcoded in the PowerShell script:
```powershell
# Old approach (deprecated)
$env:OPENAI_KEY = "hardcoded-key"
$env:DB_PASSWORD = "hardcoded-password"
```

The new approach loads from `.env`:
```env
# New approach (.env file)
OPENAI_KEY=your-key-here
DB_PASSWORD=your-password-here
```

Benefits:
- ✅ Centralized configuration
- ✅ Easier to manage and update
- ✅ Better security (keep .env out of version control)
- ✅ Standard practice across many frameworks
- ✅ Easier to share configurations (with sensitive data removed)
