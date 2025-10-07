#!/usr/bin/env python3
"""
Simple TTS testing script - saves audio files without playing them
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
        for i, voice in enumerate(voices):
            print(f"  {i}: {voice.name}")
        
        # Set voice properties
        engine.setProperty('rate', 150)    # Speed of speech
        engine.setProperty('volume', 0.9)  # Volume level (0.0 to 1.0)
        
        # Save to file only (no playing)
        audio_dir = Path("src/main/resources/static/audio-aiholo")
        audio_dir.mkdir(parents=True, exist_ok=True)
        output_file = audio_dir / "pyttsx3_test.wav"
        
        text = "Hello from pyttsx3! This is an offline text to speech test."
        engine.save_to_file(text, str(output_file))
        engine.runAndWait()
        
        if output_file.exists():
            print(f"✓ Audio saved to: {output_file} ({output_file.stat().st_size} bytes)")
            return True
        else:
            print("✗ Audio file was not created")
            return False
        
    except Exception as e:
        print(f"✗ pyttsx3 error: {e}")
        return False

def test_gtts():
    """Test gTTS - online Google TTS library"""
    try:
        from gtts import gTTS
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
        
        if output_file.exists():
            print(f"✓ Audio saved to: {output_file} ({output_file.stat().st_size} bytes)")
            
            # Test a few languages
            languages = [('en', 'English'), ('es', 'Spanish'), ('fr', 'French')]
            print("Language support:")
            for lang_code, lang_name in languages:
                try:
                    test_tts = gTTS(text=f"Hello in {lang_name}", lang=lang_code, slow=False)
                    print(f"  ✓ {lang_name} ({lang_code})")
                except Exception as e:
                    print(f"  ✗ {lang_name} ({lang_code}): {e}")
            
            return True
        else:
            print("✗ Audio file was not created")
            return False
        
    except Exception as e:
        print(f"✗ gTTS error: {e}")
        return False

def main():
    print("=== Simple TTS Library Testing ===")
    print(f"Python version: {sys.version}")
    print()
    
    results = {}
    
    print("1. Testing pyttsx3 (offline TTS):")
    results['pyttsx3'] = test_pyttsx3()
    print()
    
    print("2. Testing gTTS (Google TTS - requires internet):")
    results['gtts'] = test_gtts()
    print()
    
    print("=== Summary ===")
    for library, success in results.items():
        status = "✓ Working" if success else "✗ Failed"
        print(f"{library}: {status}")
    
    print("\n=== Integration Options for Java Application ===")
    if results['pyttsx3']:
        print("1. pyttsx3 Integration:")
        print("   - Offline TTS (no internet required)")
        print("   - Fast generation")
        print("   - Windows SAPI voices")
        print("   - Call via: ProcessBuilder with Python script")
    
    if results['gtts']:
        print("2. gTTS Integration:")
        print("   - High-quality Google voices")
        print("   - Requires internet connection")
        print("   - Multiple languages supported")
        print("   - Call via: ProcessBuilder with Python script")
    
    print("3. For Coqui TTS:")
    print("   - Need Python 3.11 or lower")
    print("   - Advanced voice cloning features")
    print("   - Multiple neural TTS models")

if __name__ == "__main__":
    main()