#!/usr/bin/env python3
"""
Test script for TTS alternatives that work with Python 3.12
This demonstrates both pyttsx3 (offline) and gTTS (online) libraries
"""

import os
import sys
from pathlib import Path

def test_pyttsx3():
    """Test pyttsx3 - offline TTS library"""
    try:
        import pyttsx3
        print("✓ pyttsx3 imported successfully")
        
        # Initialize the TTS engine
        engine = pyttsx3.init()
        
        # Get available voices
        voices = engine.getProperty('voices')
        print(f"Available voices: {len(voices)}")
        for i, voice in enumerate(voices[:3]):  # Show first 3 voices
            print(f"  {i}: {voice.name} ({voice.languages})")
        
        # Set voice properties
        engine.setProperty('rate', 150)    # Speed of speech
        engine.setProperty('volume', 0.9)  # Volume level (0.0 to 1.0)
        
        # Test speech
        text = "Hello from pyttsx3! This is an offline text to speech test."
        print(f"Speaking: {text}")
        engine.say(text)
        engine.runAndWait()
        
        # Save to file
        audio_dir = Path("src/main/resources/static/audio-aiholo")
        audio_dir.mkdir(parents=True, exist_ok=True)
        output_file = audio_dir / "pyttsx3_test.wav"
        
        engine.save_to_file(text, str(output_file))
        engine.runAndWait()
        print(f"✓ Audio saved to: {output_file}")
        
        return True
        
    except Exception as e:
        print(f"✗ pyttsx3 error: {e}")
        return False

def test_gtts():
    """Test gTTS - online Google TTS library"""
    try:
        from gtts import gTTS
        import io
        print("✓ gTTS imported successfully")
        
        # Test text
        text = "Hello from Google Text to Speech! This requires an internet connection."
        
        # Create gTTS object
        tts = gTTS(text=text, lang='en', slow=False)
        
        # Save to file
        audio_dir = Path("src/main/resources/static/audio-aiholo")
        audio_dir.mkdir(parents=True, exist_ok=True)
        output_file = audio_dir / "gtts_test.mp3"
        
        tts.save(str(output_file))
        print(f"✓ Audio saved to: {output_file}")
        print(f"Speaking: {text}")
        
        # Test different languages
        languages = [
            ('en', 'English'),
            ('es', 'Spanish'),
            ('fr', 'French'),
            ('de', 'German'),
            ('ja', 'Japanese')
        ]
        
        print("Available languages tested:")
        for lang_code, lang_name in languages:
            try:
                test_tts = gTTS(text=f"Hello in {lang_name}", lang=lang_code, slow=False)
                print(f"  ✓ {lang_name} ({lang_code})")
            except Exception as e:
                print(f"  ✗ {lang_name} ({lang_code}): {e}")
        
        return True
        
    except Exception as e:
        print(f"✗ gTTS error: {e}")
        return False

def test_coqui_tts_install():
    """Try to install Coqui TTS with Python 3.11 compatibility"""
    try:
        # Try to force install despite Python version
        print("Attempting to install Coqui TTS (may fail due to Python 3.12)...")
        import subprocess
        result = subprocess.run([
            sys.executable, "-m", "pip", "install", "TTS", "--force-reinstall", "--no-deps"
        ], capture_output=True, text=True)
        
        if result.returncode == 0:
            print("✓ Coqui TTS installed successfully")
            try:
                from TTS.api import TTS
                print("✓ TTS imported successfully")
                # List available models
                print("Available models:")
                tts = TTS()
                models = tts.list_models()
                for model in models[:5]:  # Show first 5 models
                    print(f"  - {model}")
                return True
            except Exception as e:
                print(f"✗ TTS import failed: {e}")
                return False
        else:
            print(f"✗ Installation failed: {result.stderr}")
            return False
            
    except Exception as e:
        print(f"✗ Coqui TTS installation error: {e}")
        return False

def main():
    print("=== TTS Library Testing ===")
    print(f"Python version: {sys.version}")
    print(f"Working directory: {os.getcwd()}")
    print()
    
    results = {}
    
    print("1. Testing pyttsx3 (offline TTS):")
    results['pyttsx3'] = test_pyttsx3()
    print()
    
    print("2. Testing gTTS (Google TTS - requires internet):")
    results['gtts'] = test_gtts()
    print()
    
    print("3. Testing Coqui TTS installation:")
    results['coqui'] = test_coqui_tts_install()
    print()
    
    print("=== Summary ===")
    for library, success in results.items():
        status = "✓ Working" if success else "✗ Failed"
        print(f"{library}: {status}")
    
    # Recommendations
    print("\n=== Recommendations ===")
    if results['pyttsx3']:
        print("• Use pyttsx3 for offline TTS (good for local development)")
    if results['gtts']:
        print("• Use gTTS for high-quality online TTS (requires internet)")
    if results['coqui']:
        print("• Use Coqui TTS for advanced features and voice cloning")
    else:
        print("• Consider installing Python 3.11 for Coqui TTS compatibility")
    
    print("\nFor production use in your Java application:")
    print("• Integrate pyttsx3 via Python subprocess calls")
    print("• Use gTTS for better quality when internet is available")
    print("• Keep existing Google Cloud TTS for enterprise features")

if __name__ == "__main__":
    main()