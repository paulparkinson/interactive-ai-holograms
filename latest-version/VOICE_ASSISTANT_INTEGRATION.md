# Voice Assistant Integration with Spring Boot

The multilingual voice assistant has been integrated into the Oracle AI Spring Boot application as a Spring service that runs at application startup.

## Files Added

1. **VoiceAssistantService.java** - Spring service containing all voice assistant logic
   - Location: `latest-version/src/main/java/oracleai/aiholo/VoiceAssistantService.java`
   - Handles: Porcupine wake word detection, Google Cloud STT, language detection, ChatGPT, TTS

2. **VoiceAssistantConfiguration.java** - Spring configuration for startup initialization
   - Location: `latest-version/src/main/java/oracleai/aiholo/VoiceAssistantConfiguration.java`
   - Runs the voice assistant as an ApplicationRunner bean at Spring Boot startup

## Dependencies Added to pom.xml

- `porcupine-java:4.0.1` - Wake word detection
- `google-cloud-language` - Language detection
- (google-cloud-speech and google-cloud-texttospeech were already present)

## Environment Variables

To enable and configure the voice assistant at startup, set these environment variables:

### Required
- `ENABLE_VOICE_ASSISTANT` = "true" (to enable startup initialization)
- `PORCUPINE_ACCESS_KEY` = your Picovoice access key
- `KEYWORD_PATH` = path to your wake word model file (e.g., `C:\Users\...\Hey-computer_en_windows_v4_0_0.ppn`)

### Optional
- `AUDIO_DEVICE_INDEX` = audio device index (default: -1 for default device)
- `ENABLE_LANGUAGE_DETECTION` = "true" or "false" (default: true)
- `RESPONSE_LANGUAGE` = "both", "same", or "english" (default: "both")
- `OPENAI_API_KEY` = OpenAI API key for ChatGPT

## How to Use

### Option 1: Enable at Startup
Set environment variables and the voice assistant will automatically start when the Spring Boot application starts:

```powershell
$env:ENABLE_VOICE_ASSISTANT = "true"
$env:PORCUPINE_ACCESS_KEY = "your-access-key"
$env:KEYWORD_PATH = "C:\path\to\Hey-computer.ppn"
$env:ENABLE_LANGUAGE_DETECTION = "true"
$env:RESPONSE_LANGUAGE = "both"
$env:OPENAI_API_KEY = "your-api-key"

# Run Spring Boot app (e.g., via Maven)
mvn spring-boot:run
```

### Option 2: Disable Startup Initialization
To run the Spring Boot application without voice assistant, either:
- Don't set `ENABLE_VOICE_ASSISTANT=true`, or
- Comment out the `@Bean` annotation in `VoiceAssistantConfiguration.java`

### Option 3: Manual Initialization
You can inject `VoiceAssistantService` into any Spring component and call it manually:

```java
@Autowired
private VoiceAssistantService voiceAssistantService;

// In any method:
voiceAssistantService.runDemo(
    accessKey,
    Porcupine.LIBRARY_PATH,
    Porcupine.MODEL_PATH,
    "best",
    new String[]{keywordPath},
    new float[]{0.5f},
    -1
);
```

## Behavior

When enabled and started:

1. **Wake Word Detection** - Listens for "Hey-computer" (configurable)
2. **Speech Capture** - Records up to 40 seconds of audio or until "please" equivalent is spoken
3. **Language Detection** - Automatically detects if user spoke in English, Mandarin, Spanish, French, German, Japanese, Korean, Italian, Portuguese, Russian, Arabic, or Hindi
4. **ChatGPT Processing** - Sends transcription to ChatGPT with conversation history
5. **Multilingual Response** - Returns response in the detected language (if `RESPONSE_LANGUAGE=both` or `same`)
6. **Text-to-Speech** - Plays response in appropriate language voice

## Language Trigger Detection (Option A: Dynamic Language-Aware)

The voice assistant now uses smart language-aware trigger detection:
- Detects language on the first final transcription result
- Checks for "please" equivalents ONLY in the detected language
- English speakers: says "please" → instant trigger
- Mandarin speakers: says "请", "麻烦", or "拜托" → instant trigger (once Mandarin is detected)
- Works for 10+ languages with appropriate "please" words

## Supported Languages

| Language | "Please" Trigger | TTS Voice |
|----------|------------------|-----------|
| English | "please" | en-US-Neural2-C |
| Mandarin | "请", "麻烦", "拜托" | cmn-CN-Wavenet-A |
| Spanish | "por favor" | es-ES-Neural2-A |
| French | "plaît", "s'il" | fr-FR-Neural2-A |
| German | "bitte" | de-DE-Neural2-A |
| Italian | "favore", "piacere" | it-IT-Neural2-A |
| Portuguese | "favor", "gentileza" | pt-BR-Neural2-A |
| Russian | "пожалуйста" | ru-RU-Wavenet-A |
| Japanese | "ください", "お願い" | ja-JP-Neural2-B |
| Korean | "주세요", "부탁" | ko-KR-Neural2-A |
| Arabic | "من فضلك", "لو سمحت" | ar-XA-Wavenet-A |
| Hindi | "कृपया", "प्रिय" | hi-IN-Neural2-A |

## Google Cloud APIs Required

The following Google Cloud APIs must be enabled:
1. Cloud Speech-to-Text API
2. Cloud Language API
3. Cloud Text-to-Speech API

Ensure your Google Cloud authentication is configured (typically via `GOOGLE_APPLICATION_CREDENTIALS` environment variable).

## Build and Run

```powershell
# Navigate to latest-version directory
cd C:\src\github\paulparkinson\interactive-ai-holograms\latest-version

# Build with Maven
mvn clean package -DskipTests -q

# Run the application
mvn spring-boot:run
```

Or run the generated JAR directly with environment variables set.

## Troubleshooting

- **"No audio devices found"**: Use `--show-audio-devices` flag in the standalone app to see available devices
- **"PORCUPINE_ACCESS_KEY not set"**: Get your free key from https://console.picovoice.ai/
- **"Google API error"**: Ensure APIs are enabled and credentials are configured
- **"No speech detected"**: Check microphone is working and properly configured
- **Mandarin not detected**: Make sure you speak clearly and wait for STT to recognize it's Mandarin

## Notes

- The voice assistant runs **synchronously** on startup (blocks other Spring initialization until user presses Enter or 40 seconds passes)
- To run asynchronously, modify `VoiceAssistantConfiguration.java` to use a separate thread
- Conversation history is maintained for the duration of the voice session
- The application can handle multiple voice sessions (history is reset between them)
