#!/usr/bin/env python3
"""
Python TTS script that can be called from Java application
Supports both pyttsx3 (offline) and gTTS (online)
"""

import sys
import argparse
from pathlib import Path

def tts_pyttsx3(text, output_file, voice_id=0, rate=150):
    """Generate TTS using pyttsx3 (offline)"""
    try:
        import pyttsx3
        
        engine = pyttsx3.init()
        
        # Set voice
        voices = engine.getProperty('voices')
        if voice_id < len(voices):
            engine.setProperty('voice', voices[voice_id].id)
        
        # Set rate and volume
        engine.setProperty('rate', rate)
        engine.setProperty('volume', 0.9)
        
        # Save to file
        engine.save_to_file(text, output_file)
        engine.runAndWait()
        
        if Path(output_file).exists():
            print(f"SUCCESS: Audio saved to {output_file}")
            return True
        else:
            print("ERROR: Failed to create audio file")
            return False
            
    except Exception as e:
        print(f"ERROR: pyttsx3 failed: {e}")
        return False

def tts_gtts(text, output_file, lang='en'):
    """Generate TTS using gTTS (online)"""
    try:
        from gtts import gTTS
        
        tts = gTTS(text=text, lang=lang, slow=False)
        tts.save(output_file)
        
        if Path(output_file).exists():
            print(f"SUCCESS: Audio saved to {output_file}")
            return True
        else:
            print("ERROR: Failed to create audio file")
            return False
            
    except Exception as e:
        print(f"ERROR: gTTS failed: {e}")
        return False

def main():
    parser = argparse.ArgumentParser(description='Python TTS Script for Java Integration')
    parser.add_argument('--text', required=True, help='Text to convert to speech')
    parser.add_argument('--output', required=True, help='Output audio file path')
    parser.add_argument('--engine', choices=['pyttsx3', 'gtts'], default='pyttsx3', 
                       help='TTS engine to use (default: pyttsx3)')
    parser.add_argument('--lang', default='en', help='Language code (for gTTS)')
    parser.add_argument('--voice', type=int, default=0, help='Voice ID (for pyttsx3)')
    parser.add_argument('--rate', type=int, default=150, help='Speech rate (for pyttsx3)')
    
    args = parser.parse_args()
    
    # Ensure output directory exists
    output_path = Path(args.output)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    
    print(f"TTS Engine: {args.engine}")
    print(f"Text: {args.text}")
    print(f"Output: {args.output}")
    
    if args.engine == 'pyttsx3':
        success = tts_pyttsx3(args.text, args.output, args.voice, args.rate)
    elif args.engine == 'gtts':
        success = tts_gtts(args.text, args.output, args.lang)
    
    sys.exit(0 if success else 1)

if __name__ == "__main__":
    main()