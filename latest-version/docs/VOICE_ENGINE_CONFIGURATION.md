# Voice Assistant Engine Configuration

The voice assistant now supports **two wake word detection engines**: Porcupine and OpenWakeWord.

## Engines

### 1. Porcupine (Default)
- **Commercial**: Requires API key from Picovoice
- **Accuracy**: High
- **Latency**: Low (~80ms)
- **Models**: Custom wake word models (.ppn files)
- **Platform**: Java native

### 2. OpenWakeWord
- **Open Source**: Free, no API key required
- **Accuracy**: Good
- **Latency**: Low (~80ms)  
- **Models**: Pre-trained models (hey_jarvis, alexa, hey_mycroft, etc.)
- **Platform**: Python with Java bridge

## Configuration

Set the `VOICE_ASSISTANT_ENGINE` environment variable in your `.env` file:

```bash
# Use Porcupine (default)
VOICE_ASSISTANT_ENGINE=porcupine

# Use OpenWakeWord
VOICE_ASSISTANT_ENGINE=openwakeword
```

### Porcupine Setup

1. Get an API key from [Picovoice Console](https://console.picovoice.ai/)
2. Download or create a wake word model (.ppn file)
3. Configure in `.env`:

```bash
ENABLE_VOICE_ASSISTANT=true
VOICE_ASSISTANT_ENGINE=porcupine
PORCUPINE_ACCESS_KEY=your-access-key-here
KEYWORD_PATH=C:\path\to\Hey-computer_en_windows_v4_0_0.ppn
```

### OpenWakeWord Setup

1. Install Python dependencies:
```bash
cd wakeupwords
pip install -r requirements.txt
```

2. Configure in `.env`:
```bash
ENABLE_VOICE_ASSISTANT=true
VOICE_ASSISTANT_ENGINE=openwakeword
OPENWAKEWORD_SCRIPT_PATH=wakeupwords/openwakeword_bridge.py
OPENWAKEWORD_MODEL=hey_jarvis
```

**Available OpenWakeWord Models:**
- `hey_jarvis` (default)
- `alexa`
- `hey_mycroft`
- `hey_rhasspy`
- And more from the OpenWakeWord model library

## Architecture

### Porcupine Flow
```
Microphone → Java Audio Capture → Porcupine Native Library → Wake Word Detection
```

### OpenWakeWord Flow
```
Microphone → Java Audio Capture → Python Bridge (stdin/stdout) → OpenWakeWord → Wake Word Detection
```

## Files

### Java Implementation
- `VoiceAssistantPorcupineService.java` - Porcupine engine implementation
- `VoiceAssistantPorcupineConfiguration.java` - Porcupine Spring configuration
- `VoiceAssistantOpenWakeWordService.java` - OpenWakeWord engine implementation
- `VoiceAssistantOpenWakeWordConfiguration.java` - OpenWakeWord Spring configuration
- `Configuration.java` - Centralized configuration with engine selection

### Python Bridge
- `wakeupwords/openwakeword_bridge.py` - Python script for OpenWakeWord integration
- `wakeupwords/requirements.txt` - Python dependencies

## Switching Engines

Simply change the `VOICE_ASSISTANT_ENGINE` variable in `.env` and restart the application:

```bash
# Switch to OpenWakeWord
VOICE_ASSISTANT_ENGINE=openwakeword

# Switch back to Porcupine
VOICE_ASSISTANT_ENGINE=porcupine
```

No code changes required!

## Troubleshooting

### Porcupine Issues
- **"Activation limit exceeded"**: Get a new API key or use OpenWakeWord
- **"JAVA_HOME not defined"**: Set JAVA_HOME or use auto-detection

### OpenWakeWord Issues
- **"Python not found"**: Install Python 3.8+ and add to PATH
- **"Module 'openwakeword' not found"**: Run `pip install -r wakeupwords/requirements.txt`
- **"Model not found"**: Check model name spelling in OPENWAKEWORD_MODEL

## Performance Comparison

| Feature | Porcupine | OpenWakeWord |
|---------|-----------|--------------|
| Cost | Paid API key | Free |
| Setup | Easy | Requires Python |
| Latency | ~80ms | ~80ms |
| Accuracy | High | Good |
| Custom Models | Yes (.ppn files) | Limited |
| Offline | Yes | Yes |

## Recommendation

- **Production/Commercial**: Use Porcupine for highest accuracy
- **Open Source/Personal**: Use OpenWakeWord for free solution
- **Testing**: Start with OpenWakeWord, upgrade to Porcupine if needed
