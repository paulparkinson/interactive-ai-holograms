# TTS Implementation Comparison

## Overview
This document compares the different Text-to-Speech (TTS) implementations available in your AI Hologram project, including the differences between `TTSAndAudio2Face.java` and `TTSLocal.java`, plus the newly installed Python TTS alternatives.

## Current Java TTS Implementations

### 1. TTSAndAudio2Face.java
**Purpose**: Google Cloud TTS with Audio2Face integration and local audio playback
**Key Features**:
- Uses Google Cloud Text-to-Speech API
- Supports Audio2Face integration via REST API calls
- Local audio playback using Java AudioSystem
- Dynamic file path handling via environment variables
- Conditional Audio2Face support via `IS_AUDIO2FACE` environment variable

**Key Methods**:
```java
public static void TTS(String fileName, String text, String languageCode, String voicename)
public static void sendToAudio2Face(String fileName)
public static void playAudioFile(String filename)
```

**Dependencies**:
- Google Cloud Text-to-Speech library
- Spring RestTemplate for Audio2Face API
- Java AudioSystem for local playback
- Environment variable: `IS_AUDIO2FACE`

**Audio Format**: WAV (LINEAR16 encoding)
**Output Directory**: `AIHoloController.AUDIO_DIR_PATH` (environment variable)

### 2. TTSLocal.java
**Purpose**: Dual implementation - Google Cloud TTS + Local Piper TTS
**Key Features**:
- **Primary**: Local Piper TTS executable (Windows-specific)
- **Fallback**: Google Cloud Text-to-Speech API (commented out)
- Audio2Face integration (same as TTSAndAudio2Face)
- Hardcoded Piper executable path
- Model path flexibility (supports full paths or filenames)

**Key Methods**:
```java
public static void TTS(String fileName, String text, String languageCode, String voicename)
public static void sendToAudio2Face(String fileName)
```

**Dependencies**:
- Piper TTS executable: `C:\Users\holoai\Downloads\piper_windows_amd64\piper\piper.exe`
- Default model: `en_US-kathleen-low.onnx`
- Spring RestTemplate for Audio2Face API
- Process execution for Piper TTS

**Audio Format**: WAV (Piper output)
**Notable**: No local audio playback method (missing `playAudioFile`)

## Key Differences Between Java Implementations

| Feature | TTSAndAudio2Face.java | TTSLocal.java |
|---------|----------------------|---------------|
| **Primary TTS Engine** | Google Cloud TTS | Local Piper TTS |
| **Fallback TTS** | None | Google Cloud TTS (commented) |
| **Local Playback** | ✅ `playAudioFile()` method | ❌ Missing |
| **Configuration** | Environment variables | Hardcoded paths |
| **Platform** | Cross-platform | Windows-specific |
| **Internet Required** | ✅ Yes | ❌ No (local Piper) |
| **Voice Quality** | High (Google) | Good (Piper neural) |
| **Voice Options** | Google Cloud voices | Piper ONNX models |
| **Audio2Face** | ✅ Conditional | ✅ Always |
| **File Handling** | Dynamic paths | Dynamic paths |
| **Error Handling** | Comprehensive | Basic |

## New Python TTS Alternatives

### 3. pyttsx3 (Offline)
**Installation**: ✅ Working with Python 3.12
**Features**:
- Windows SAPI voices (Microsoft David, Zira)
- No internet required
- Adjustable speech rate and volume
- Cross-platform support
- Direct integration via subprocess

**Usage**:
```bash
py python_tts.py --text "Hello World" --output audio.wav --engine pyttsx3
```

**Pros**:
- Offline operation
- Fast generation
- No external dependencies
- Free to use

**Cons**:
- Limited voice quality
- Platform-dependent voices
- No advanced features

### 4. gTTS (Google Text-to-Speech Online)
**Installation**: ✅ Working with Python 3.12
**Features**:
- High-quality Google voices
- 100+ languages supported
- MP3 output format
- Simple API

**Usage**:
```bash
py python_tts.py --text "Hello World" --output audio.mp3 --engine gtts --lang en
```

**Pros**:
- Excellent voice quality
- Many languages
- Same quality as Google Cloud TTS
- Simple integration

**Cons**:
- Requires internet
- Rate limiting possible
- MP3 format (needs conversion for some uses)

### 5. Coqui TTS (Advanced Neural TTS)
**Installation**: ❌ Failed (requires Python <3.12)
**Features**:
- Voice cloning capabilities
- 1100+ language models
- Neural TTS models
- Advanced customization

**To Install**: Need Python 3.11 or lower
```bash
# With Python 3.11
pip install TTS
```

## Integration Recommendations

### For Your Java Application

#### Option 1: Keep Current + Add Python Fallback
```java
// Enhance TTSAndAudio2Face.java with Python TTS fallback
public static void TTS(String fileName, String text, String languageCode, String voicename) {
    try {
        // Try Google Cloud TTS first
        googleCloudTTS(fileName, text, languageCode, voicename);
    } catch (Exception e) {
        // Fallback to Python TTS
        pythonTTSFallback(fileName, text, languageCode);
    }
}

private static void pythonTTSFallback(String fileName, String text, String languageCode) {
    ProcessBuilder pb = new ProcessBuilder(
        "py", "python_tts.py", 
        "--text", text, 
        "--output", AIHoloController.AUDIO_DIR_PATH + fileName,
        "--engine", "pyttsx3"  // or "gtts"
    );
    // Execute and handle result
}
```

#### Option 2: Create Unified TTS Manager
```java
public class TTSManager {
    public enum TTSEngine {
        GOOGLE_CLOUD,
        PIPER_LOCAL,
        PYTHON_PYTTSX3,
        PYTHON_GTTS
    }
    
    public static void generateTTS(String fileName, String text, TTSEngine engine) {
        switch (engine) {
            case GOOGLE_CLOUD -> TTSAndAudio2Face.TTS(fileName, text, "en-US", "en-US-Wavenet-D");
            case PIPER_LOCAL -> TTSLocal.TTS(fileName, text, "en-US", "en_US-kathleen-low.onnx");
            case PYTHON_PYTTSX3 -> callPythonTTS(fileName, text, "pyttsx3");
            case PYTHON_GTTS -> callPythonTTS(fileName, text, "gtts");
        }
    }
}
```

### Configuration Priority
1. **Production**: Google Cloud TTS (TTSAndAudio2Face.java)
2. **Development/Offline**: Python pyttsx3 
3. **High Quality Offline**: Piper TTS (TTSLocal.java) - if paths fixed
4. **Internet Fallback**: Python gTTS

### Environment Variables to Add
```bash
# TTS Configuration
TTS_ENGINE=google_cloud  # google_cloud, piper_local, python_pyttsx3, python_gtts
TTS_PYTHON_PATH=py
TTS_PIPER_PATH=C:\path\to\piper\piper.exe
TTS_FALLBACK_ENGINE=python_pyttsx3
```

## Next Steps

1. **Fix TTSLocal.java** - Make Piper path configurable via environment variables
2. **Add Python Integration** - Create Java wrapper for Python TTS scripts
3. **Install Python 3.11** - To enable Coqui TTS for advanced features
4. **Create TTS Manager** - Unified interface for all TTS engines
5. **Add Configuration** - Environment-based TTS engine selection

This gives you multiple TTS options ranging from cloud-based high quality to completely offline solutions, with fallback strategies for reliability.