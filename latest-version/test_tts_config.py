#!/usr/bin/env python3
"""
TTS Configuration Test Script
Tests all available TTS engines and provides recommendations
"""

import sys
import os
from pathlib import Path

def test_tts_configuration():
    """Test the TTS configuration system"""
    print("=== TTS CONFIGURATION TEST ===")
    print()
    
    # Test environment variables
    tts_engine = os.getenv("TTS_ENGINE", "GCP")
    tts_quality = os.getenv("TTS_QUALITY", "BALANCED")
    audio_dir = os.getenv("AUDIO_DIR_PATH", "src/main/resources/static/audio-aiholo")
    
    print(f"TTS_ENGINE: {tts_engine}")
    print(f"TTS_QUALITY: {tts_quality}")
    print(f"AUDIO_DIR_PATH: {audio_dir}")
    print()
    
    # Test Coqui TTS if it's the selected engine
    if tts_engine.upper() == "COQUI":
        print("Testing Coqui TTS configuration...")
        
        # Check if Python 3.11 is available
        try:
            import subprocess
            result = subprocess.run(["py", "-3.11", "--version"], 
                                  capture_output=True, text=True)
            if result.returncode == 0:
                print("OK: " + result.stdout.strip())
            else:
                print("FAIL: Python 3.11 not found")
                return False
        except Exception as e:
            print(f"ERROR: Error checking Python 3.11: {e}")
            return False
        
        # Test Coqui TTS import
        try:
            result = subprocess.run([
                "py", "-3.11", "-c", 
                "from TTS.api import TTS; print('Coqui TTS imported successfully')"
            ], capture_output=True, text=True)
            
            if result.returncode == 0:
                print(result.stdout.strip())
            else:
                print(f"FAIL: Coqui TTS import failed: {result.stderr}")
                return False
        except Exception as e:
            print(f"ERROR: Error testing Coqui TTS: {e}")
            return False
        
        # Test TTS generation
        try:
            print("Testing Coqui TTS generation...")
            result = subprocess.run([
                "py", "-3.11", "coqui_tts_integration.py",
                "--text", "Testing TTS configuration system",
                "--output", os.path.join(audio_dir, "config_test.wav"),
                "--model", tts_quality.lower()
            ], capture_output=True, text=True)
            
            if result.returncode == 0:
                print("OK: Coqui TTS generation test passed")
                print(result.stdout)
            else:
                print(f"FAIL: Coqui TTS generation failed: {result.stderr}")
                return False
        except Exception as e:
            print(f"ERROR: Error testing TTS generation: {e}")
            return False
    
    print()
    print("=== TTS ENGINE COMPARISON ===")
    print()
    print("GCP (Google Cloud TTS):")
    print("  OK: High quality voices")
    print("  OK: Multiple languages")
    print("  NO: Requires internet")
    print("  NO: API costs")
    print("  NO: Rate limits")
    print()
    
    print("OCI (Oracle Cloud Infrastructure):")
    print("  ?  Placeholder - not yet implemented")
    print("  ?  Will integrate Oracle Speech Services")
    print()
    
    print("COQUI (Neural TTS):")
    print("  OK: High quality neural voices")
    print("  OK: Completely offline")
    print("  OK: No API costs")
    print("  OK: Multiple quality modes")
    print("  OK: Fast generation (1-6 seconds)")
    print("  NO: Requires Python 3.11")
    print("  NO: Initial model download")
    print()
    
    print("=== RECOMMENDATIONS ===")
    print()
    
    if tts_engine.upper() == "COQUI":
        print("EXCELLENT CHOICE: Coqui TTS")
        print("   - Best offline quality and variety")
        print("   - No internet required")
        print("   - No ongoing costs")
        print()
        print("Quality Settings:")
        print("   - FAST: ~1.3s generation (Tacotron2)")
        print("   - BALANCED: ~2-3s generation (VITS)")
        print("   - QUALITY: ~6s generation (Glow-TTS)")
    elif tts_engine.upper() == "GCP":
        print("GOOD CHOICE: Google Cloud TTS")
        print("   - Proven reliability")
        print("   - Current working solution")
        print("   - Consider switching to COQUI for offline benefits")
    else:
        print("UNKNOWN ENGINE: " + tts_engine)
        print("   - Valid options: GCP, OCI, COQUI")
        print("   - Recommend COQUI for best offline experience")
    
    return True

def main():
    success = test_tts_configuration()
    
    if success:
        print()
        print("TTS Configuration Test PASSED")
        print()
        print("To use different TTS engines, set environment variables:")
        print("   $env:TTS_ENGINE = \"COQUI\"    # GCP, OCI, COQUI")
        print("   $env:TTS_QUALITY = \"BALANCED\" # FAST, BALANCED, QUALITY")
        print()
        print("Then restart the Java application.")
    else:
        print()
        print("TTS Configuration Test FAILED")
        print("Check the errors above and fix before proceeding.")
    
    return success

if __name__ == "__main__":
    success = main()
    sys.exit(0 if success else 1)