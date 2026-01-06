#!/usr/bin/env python3
"""
Production Coqui TTS Script for Java Integration
High-quality offline neural TTS with multiple model support
"""

import sys
import os
import argparse
import time
import locale
from pathlib import Path
from typing import Dict, Optional, Tuple

# Force UTF-8 encoding in case the parent process uses a legacy Windows codepage
os.environ.setdefault("PYTHONUTF8", "1")
os.environ.setdefault("PYTHONIOENCODING", "utf-8")

try:
    locale.setlocale(locale.LC_ALL, "en_US.UTF-8")
except locale.Error:
    pass

try:
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")
except (AttributeError, ValueError):
    pass

# Built-in language presets for Coqui models. Keys are normalized language codes.
LANGUAGE_PRESETS: Dict[str, Dict[str, Optional[str]]] = {
    "en": {"model": "tts_models/en/ljspeech/glow-tts"},
    "en-us": {"model": "tts_models/en/ljspeech/glow-tts"},
    "en-gb": {"model": "tts_models/en/ljspeech/vits"},
    "en-au": {"model": "tts_models/en/ljspeech/vits"},
    "es": {"model": "tts_models/es/css10/vits"},
    "es-es": {"model": "tts_models/es/css10/vits"},
    "es-mx": {"model": "tts_models/es/css10/vits"},
    "fr": {"model": "tts_models/fr/css10/vits"},
    "fr-fr": {"model": "tts_models/fr/css10/vits"},
    "de": {"model": "tts_models/de/thorsten/tacotron2-DDC"},
    "de-de": {"model": "tts_models/de/thorsten/tacotron2-DDC"},
    "pt": {"model": "tts_models/pt/cv/vits"},
    "pt-br": {"model": "tts_models/pt/cv/vits"},
    "it": {"model": "tts_models/it/css10/vits"},
    "it-it": {"model": "tts_models/it/css10/vits"},
    "ro": {"model": "tts_models/ro/css10/vits"},
    "ro-ro": {"model": "tts_models/ro/css10/vits"},
    "hi": {"model": "tts_models/hi/cv/vits"},
    "hi-in": {"model": "tts_models/hi/cv/vits"},
    "ar": {"model": "tts_models/ar/cv/vits"},
    "ar-ae": {"model": "tts_models/ar/cv/vits"},
    "ga": {"model": "tts_models/ga/cv/vits"},
    "ga-ga": {"model": "tts_models/ga/cv/vits"},
    "ga-ie": {"model": "tts_models/ga/cv/vits"},
    "ja": {"model": "tts_models/ja/kokoro/tacotron2-DDC"},
    "ja-jp": {"model": "tts_models/ja/kokoro/tacotron2-DDC"},
    "zh": {"model": "tts_models/zh-CN/baker/tacotron2-DDC"},
    "zh-cn": {"model": "tts_models/zh-CN/baker/tacotron2-DDC"},
    "zh-sg": {"model": "tts_models/zh-CN/baker/tacotron2-DDC"}
}

# Optional voice-name overrides. Keys are normalized voice names from the UI.
VOICE_PRESETS: Dict[str, Dict[str, Optional[str]]] = {
    "en-us-chirp3-hd-aoede": {"model": "tts_models/en/ljspeech/glow-tts"},
    "en-us-wavenet-a": {"model": "tts_models/en/ljspeech/vits"},
    "es-es-wavenet-d": {"model": "tts_models/es/css10/vits"},
    "es-us-wavenet-a": {"model": "tts_models/es/css10/vits"},
    "pt-br-wavenet-d": {"model": "tts_models/pt/cv/vits"},
    "fr-fr-wavenet-a": {"model": "tts_models/fr/css10/vits"},
    "de-de-wavenet-a": {"model": "tts_models/de/thorsten/tacotron2-DDC"},
    "it-it-wavenet-a": {"model": "tts_models/it/css10/vits"},
    "ga-ga-wavenet-a": {"model": "tts_models/ga/cv/vits"},
    "ga-ie-wavenet-a": {"model": "tts_models/ga/cv/vits"},
    "ar-ae-wavenet-a": {"model": "tts_models/ar/cv/vits"},
    "ja-jp-wavenet-a": {"model": "tts_models/ja/kokoro/tacotron2-DDC"},
    "hi-in-wavenet-a": {"model": "tts_models/hi/cv/vits"},
    "ro-ro-wavenet-a": {"model": "tts_models/ro/css10/vits"},
    "en-gb-wavenet-a": {"model": "tts_models/en/ljspeech/vits"},
    "en-au-wavenet-a": {"model": "tts_models/en/ljspeech/vits"},
    "cmn-cn-wavenet-a": {"model": "tts_models/zh-CN/baker/tacotron2-DDC"},
    "en-us-wavenet-c": {"model": "tts_models/en/ljspeech/vits"},
    "en-us-wavenet-e": {"model": "tts_models/en/ljspeech/vits"},
    "en-us-wavenet-f": {"model": "tts_models/en/ljspeech/glow-tts"},
    "en-us-wavenet-g": {"model": "tts_models/en/ljspeech/glow-tts"},
    "en-us-wavenet-h": {"model": "tts_models/en/ljspeech/glow-tts"},
    "en-us-standard-c": {"model": "tts_models/en/ljspeech/vits"},
    "en-us-standard-e": {"model": "tts_models/en/ljspeech/vits"},
    "en-us-standard-f": {"model": "tts_models/en/ljspeech/vits"},
    "en-us-standard-g": {"model": "tts_models/en/ljspeech/vits"},
    "en-us-standard-h": {"model": "tts_models/en/ljspeech/vits"},
    "en-us-news-k": {"model": "tts_models/en/ljspeech/glow-tts"},
    "en-us-news-l": {"model": "tts_models/en/ljspeech/glow-tts"},
    "en-us-neural2-c": {"model": "tts_models/en/ljspeech/glow-tts"},
    "en-us-neural2-e": {"model": "tts_models/en/ljspeech/glow-tts"},
    "en-us-neural2-f": {"model": "tts_models/en/ljspeech/glow-tts"},
    "en-us-neural2-g": {"model": "tts_models/en/ljspeech/glow-tts"},
    "en-us-neural2-h": {"model": "tts_models/en/ljspeech/glow-tts"},
    "en-us-chirp-hd-f": {"model": "tts_models/en/ljspeech/glow-tts"},
    "en-us-chirp-hd-o": {"model": "tts_models/en/ljspeech/glow-tts"},
    "en-us-studio-o": {"model": "tts_models/en/ljspeech/glow-tts"},
}


def _normalize(value: Optional[str]) -> Optional[str]:
    if value is None:
        return None
    return value.strip().lower().replace("_", "-")


def resolve_voice_configuration(
    requested_model: str,
    language_code: Optional[str],
    voice_name: Optional[str]
) -> Tuple[str, Optional[str], Optional[str]]:
    """Resolve the best-fit Coqui model, speaker, and language."""
    normalized_language = _normalize(language_code) if language_code else None
    normalized_voice = _normalize(voice_name) if voice_name else None

    resolved_model = requested_model
    resolved_speaker: Optional[str] = None
    resolved_language: Optional[str] = None

    # Voice-specific overrides take priority
    if normalized_voice and normalized_voice in VOICE_PRESETS:
        preset = VOICE_PRESETS[normalized_voice]
        resolved_model = preset.get("model", resolved_model)
        resolved_speaker = preset.get("speaker")
        resolved_language = preset.get("language")

    # Language-specific overrides if voice not specified or incomplete
    if normalized_language and (normalized_language in LANGUAGE_PRESETS):
        preset = LANGUAGE_PRESETS[normalized_language]
        resolved_model = preset.get("model", resolved_model)
        resolved_language = preset.get("language", resolved_language)
        if preset.get("speaker") and not resolved_speaker:
            resolved_speaker = preset.get("speaker")

    # Environment variable overrides (highest priority)
    if normalized_voice:
        env_key = f"COQUI_MODEL_VOICE_{normalized_voice.replace('-', '_').upper()}"
        env_model = os.getenv(env_key)
        if env_model:
            resolved_model = env_model

    if normalized_language:
        env_key = f"COQUI_MODEL_LANG_{normalized_language.replace('-', '_').upper()}"
        env_model = os.getenv(env_key)
        if env_model:
            resolved_model = env_model

    return resolved_model, resolved_speaker, resolved_language


def coqui_tts_generate(
    text,
    output_file,
    model='tacotron2-DDC',
    lang='en-US',
    voice_speed=1.0,
    voice_name: Optional[str] = None
):
    """Generate TTS using Coqui TTS with specified model"""
    try:
        from TTS.api import TTS
        
        # Model mapping for easier usage
        model_map = {
            'tacotron2': 'tts_models/en/ljspeech/tacotron2-DDC',
            'tacotron2-DDC': 'tts_models/en/ljspeech/tacotron2-DDC',
            'glow-tts': 'tts_models/en/ljspeech/glow-tts', 
            'vits': 'tts_models/en/ljspeech/vits',
            'vits-en': 'tts_models/en/ljspeech/vits',
            'fast': 'tts_models/en/ljspeech/tacotron2-DDC',      # Fastest
            'quality': 'tts_models/en/ljspeech/glow-tts',       # Best quality
            'balanced': 'tts_models/en/ljspeech/tacotron2-DDC'   # Good balance, no eSpeak needed
        }
        
        # Get full model name
        full_model_name = model_map.get(model, model)

        # Resolve overrides based on requested language and voice
        full_model_name, speaker_override, language_override = resolve_voice_configuration(
            full_model_name,
            lang,
            voice_name
        )
        
        print(f"Using model: {full_model_name}")
        print(f"Text: {text}")
        print(f"Output: {output_file}")
        if lang:
            print(f"Language code: {lang}")
        if voice_name:
            print(f"Voice name: {voice_name}")
        if speaker_override:
            print(f"Speaker override: {speaker_override}")
        if language_override:
            print(f"Internal language hint: {language_override}")
        
        # Ensure output directory exists
        output_path = Path(output_file)
        output_path.parent.mkdir(parents=True, exist_ok=True)
        
        # Initialize TTS with model
        tts = TTS(model_name=full_model_name, progress_bar=False)
        
        # Generate audio
        start_time = time.time()
        tts_kwargs = {
            "text": text,
            "file_path": output_file
        }

        if language_override:
            tts_kwargs["language"] = language_override

        if speaker_override:
            tts_kwargs["speaker"] = speaker_override

        if voice_speed and voice_speed != 1.0:
            tts_kwargs["speed"] = voice_speed

        tts.tts_to_file(**tts_kwargs)
        generation_time = time.time() - start_time
        
        if Path(output_file).exists():
            file_size = Path(output_file).stat().st_size
            print(f"SUCCESS: Audio generated in {generation_time:.2f}s")
            print(f"File size: {file_size} bytes")
            print(f"Output: {output_file}")
            return True
        else:
            print("ERROR: Failed to generate audio file")
            return False
            
    except Exception as e:
        print(f"ERROR: Coqui TTS failed: {e}")
        return False

def list_available_models():
    """List available Coqui TTS models"""
    try:
        from TTS.api import TTS
        
        tts = TTS()
        models = tts.list_models()
        
        print("Available Coqui TTS Models:")
        print("=" * 50)
        
        # Group models by type
        english_models = []
        multilingual_models = []
        other_models = []
        
        for model in models.list_tts_models():
            if 'en/' in model:
                english_models.append(model)
            elif 'multilingual' in model:
                multilingual_models.append(model)
            else:
                other_models.append(model)
        
        print("\nENGLISH MODELS (Single Speaker):")
        for model in english_models[:10]:  # Show first 10
            print(f"  - {model}")
        
        print("\nMULTILINGUAL MODELS:")
        for model in multilingual_models[:5]:  # Show first 5
            print(f"  - {model}")
        
        print("\nPREDEFINED SHORTCUTS:")
        print("  - fast      : Fastest generation (Tacotron2)")
        print("  - quality   : Best quality (Glow-TTS)")
        print("  - balanced  : Best balance (VITS)")
        
        return True
        
    except Exception as e:
        print(f"ERROR: Failed to list models: {e}")
        return False

def main():
    parser = argparse.ArgumentParser(description='Coqui TTS Script for Java Integration')
    
    # Main operation
    parser.add_argument('--text', help='Text to convert to speech')
    parser.add_argument('--output', help='Output audio file path')
    parser.add_argument('--model', default='fast', 
                       help='TTS model to use (fast, quality, balanced, tacotron2, glow-tts, vits)')
    parser.add_argument('--lang', default='en-US', help='Language code to guide Coqui model selection')
    parser.add_argument('--voice', default=None, help='Preferred voice name (maps to Coqui presets)')
    parser.add_argument('--speed', type=float, default=1.0, help='Voice speed multiplier')
    
    # Utility operations
    parser.add_argument('--list-models', action='store_true', 
                       help='List available models and exit')
    parser.add_argument('--test', action='store_true',
                       help='Run a quick test and exit')
    
    args = parser.parse_args()
    
    # Handle utility operations
    if args.list_models:
        success = list_available_models()
        sys.exit(0 if success else 1)
    
    if args.test:
        test_text = "This is a test of Coqui TTS integration."
        test_output = "src/main/resources/static/audio-aiholo/coqui_test.wav"
        print("Running Coqui TTS test...")
        success = coqui_tts_generate(test_text, test_output, 'fast')
        print(f"Test {'PASSED' if success else 'FAILED'}")
        sys.exit(0 if success else 1)
    
    # Main TTS generation
    if not args.text or not args.output:
        print("ERROR: --text and --output are required for TTS generation")
        parser.print_help()
        sys.exit(1)
    
    success = coqui_tts_generate(
        text=args.text,
        output_file=args.output,
        model=args.model,
        lang=args.lang,
        voice_speed=args.speed,
        voice_name=args.voice
    )
    
    sys.exit(0 if success else 1)

if __name__ == "__main__":
    main()