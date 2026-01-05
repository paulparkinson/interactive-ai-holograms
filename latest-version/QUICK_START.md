# Voice Assistant - Quick Start Guide

## For Windows Users

### Step 1: Set Environment Variables
```powershell
$env:ENABLE_VOICE_ASSISTANT = "true"
$env:PORCUPINE_ACCESS_KEY = "pqnRedDJiDceXTd7FFghk3tyYLxsaVGSvtc+9qClqtj0BJvnG3p5qw=="
$env:KEYWORD_PATH = "C:\Users\Ruirui\Downloads\Hey-computer_en_windows_v4_0_0\Hey-computer_en_windows_v4_0_0.ppn"
$env:OPENAI_API_KEY = "your-openai-api-key"
```

### Step 2: Run the Startup Script
```powershell
cd C:\src\github\paulparkinson\interactive-ai-holograms\latest-version
powershell -ExecutionPolicy Bypass -File .\build_and_run_with_voice.ps1
```

That's it! The application will build and start with voice assistant enabled.

---

## For Linux/macOS Users

### Step 1: Set Environment Variables
```bash
export ENABLE_VOICE_ASSISTANT="true"
export PORCUPINE_ACCESS_KEY="pqnRedDJiDceXTd7FFghk3tyYLxsaVGSvtc+9qClqtj0BJvnG3p5qw=="
export KEYWORD_PATH="/path/to/Hey-computer_en_linux_v3_0_0.ppn"
export OPENAI_API_KEY="your-openai-api-key"
```

### Step 2: Run the Startup Script
```bash
cd /path/to/latest-version
chmod +x build_and_run_with_voice.sh
./build_and_run_with_voice.sh
```

---

## How It Works

1. **Wake Word**: Say "Hey-computer" to activate
2. **Listen**: Speak your question (up to 40 seconds)
3. **Say "Please"**: For instant trigger (or wait 40 seconds)
   - English: "please"
   - Mandarin: "请", "麻烦", "拜托"
   - Spanish: "por favor"
   - French: "plaît"
   - German: "bitte"
   - And 6 more languages...
4. **Get Answer**: ChatGPT responds in your language
5. **Hear Response**: TTS plays answer back to you

---

## Disable Voice Assistant

If you don't want voice assistant to run at startup, either:

**Option 1**: Don't set `ENABLE_VOICE_ASSISTANT` environment variable

**Option 2**: Use the existing startup script
```powershell
# Windows
powershell -ExecutionPolicy Bypass -File .\build_and_run_example.ps1

# Linux/macOS
./build_and_run_example.sh
```

**Option 3**: Run Spring Boot directly without environment variable
```bash
mvn spring-boot:run
```

---

## Manual Operation (Advanced)

Inject the service into any Spring component:

```java
@Autowired
private VoiceAssistantService voiceAssistantService;

public void startVoiceAssistant() {
    voiceAssistantService.runDemo(
        accessKey,
        Porcupine.LIBRARY_PATH,
        Porcupine.MODEL_PATH,
        "best",
        new String[]{keywordPath},
        new float[]{0.5f},
        -1
    );
}
```

---

## Files You Need to Know

| File | Purpose |
|------|---------|
| `VoiceAssistantService.java` | Core voice assistant logic |
| `VoiceAssistantConfiguration.java` | Spring startup configuration |
| `build_and_run_with_voice.ps1` | Windows startup script |
| `build_and_run_with_voice.sh` | Linux/macOS startup script |
| `VOICE_ASSISTANT_INTEGRATION.md` | Full documentation |
| `MIGRATION_COMPLETE.md` | Migration summary |

---

## Troubleshooting

**No sound output?**
- Check microphone is working
- Check audio device index (try different values)
- Verify speakers are connected

**STT not recognizing speech?**
- Speak louder and clearer
- Check Google Cloud APIs are enabled
- Verify internet connection

**"Please" trigger not working?**
- Wait 5-10 seconds for language detection
- Say "please" in the correct language
- For Mandarin: clearly pronounce "请"

---

## System Requirements

- Java 11+
- Maven 3.9+
- Google Cloud credentials (set `GOOGLE_APPLICATION_CREDENTIALS`)
- Microphone and speakers
- Internet connection (for Google Cloud APIs and ChatGPT)

---

For complete documentation, see: `VOICE_ASSISTANT_INTEGRATION.md`
