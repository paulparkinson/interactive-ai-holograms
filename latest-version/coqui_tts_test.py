#!/usr/bin/env python3
"""
Coqui TTS Testing Script - Tests high-quality offline TTS models
"""

import os
import sys
import time
from pathlib import Path

def test_coqui_tts_models():
    """Test various Coqui TTS models for quality and performance"""
    try:
        from TTS.api import TTS
        print("✓ Coqui TTS imported successfully")
        
        # Audio output directory
        audio_dir = Path("src/main/resources/static/audio-aiholo")
        audio_dir.mkdir(parents=True, exist_ok=True)
        
        # Test text
        test_text = "Hello, this is a test of Coqui TTS high quality neural text to speech."
        
        # Models to test - focusing on best offline quality
        models_to_test = [
            {
                "name": "tts_models/multilingual/multi-dataset/xtts_v2",
                "description": "XTTS v2 - Best multilingual model with voice cloning",
                "type": "multilingual_voice_cloning",
                "requires_speaker": True
            },
            {
                "name": "tts_models/en/ljspeech/tacotron2-DDC",
                "description": "Tacotron2 - High quality English single speaker",
                "type": "single_speaker",
                "requires_speaker": False
            },
            {
                "name": "tts_models/en/ljspeech/vits",
                "description": "VITS - Fast and high quality English",
                "type": "single_speaker",
                "requires_speaker": False
            },
            {
                "name": "tts_models/en/ljspeech/glow-tts",
                "description": "Glow-TTS - Fast parallel generation",
                "type": "single_speaker",
                "requires_speaker": False
            },
            {
                "name": "tts_models/multilingual/multi-dataset/your_tts",
                "description": "YourTTS - Multilingual with voice cloning",
                "type": "multilingual_voice_cloning",
                "requires_speaker": True
            }
        ]
        
        results = []
        
        for model_info in models_to_test:
            model_name = model_info["name"]
            print(f"\n=== Testing {model_name} ===")
            print(f"Description: {model_info['description']}")
            
            try:
                start_time = time.time()
                
                # Initialize TTS with model
                print("Loading model...")
                tts = TTS(model_name=model_name, progress_bar=False)
                
                load_time = time.time() - start_time
                print(f"Model loaded in {load_time:.2f} seconds")
                
                # Generate audio
                output_file = audio_dir / f"coqui_{model_name.split('/')[-1]}.wav"
                
                generation_start = time.time()
                
                if model_info["requires_speaker"]:
                    # For models that support voice cloning, we'll use a simple text without speaker reference for now
                    # In production, you would provide a speaker_wav file
                    try:
                        # Try without speaker first
                        tts.tts_to_file(text=test_text, file_path=str(output_file))
                    except Exception as e:
                        print(f"Model requires speaker reference: {e}")
                        # Skip this model for now or use a default speaker
                        results.append({
                            "model": model_name,
                            "status": "requires_speaker_reference",
                            "load_time": load_time,
                            "generation_time": 0,
                            "file_size": 0,
                            "description": model_info["description"]
                        })
                        continue
                else:
                    tts.tts_to_file(text=test_text, file_path=str(output_file))
                
                generation_time = time.time() - generation_start
                
                # Check output
                if output_file.exists():
                    file_size = output_file.stat().st_size
                    print(f"✓ Generated: {output_file}")
                    print(f"✓ File size: {file_size} bytes")
                    print(f"✓ Generation time: {generation_time:.2f} seconds")
                    
                    results.append({
                        "model": model_name,
                        "status": "success",
                        "load_time": load_time,
                        "generation_time": generation_time,
                        "file_size": file_size,
                        "description": model_info["description"]
                    })
                else:
                    print("✗ Audio file not generated")
                    results.append({
                        "model": model_name,
                        "status": "failed_generation",
                        "load_time": load_time,
                        "generation_time": generation_time,
                        "file_size": 0,
                        "description": model_info["description"]
                    })
                
            except Exception as e:
                print(f"✗ Error with {model_name}: {e}")
                results.append({
                    "model": model_name,
                    "status": f"error: {str(e)[:50]}...",
                    "load_time": 0,
                    "generation_time": 0,
                    "file_size": 0,
                    "description": model_info["description"]
                })
        
        return results
        
    except Exception as e:
        print(f"✗ Coqui TTS error: {e}")
        return []

def print_results_summary(results):
    """Print a summary of test results"""
    print("\n" + "="*80)
    print("COQUI TTS MODEL TEST RESULTS")
    print("="*80)
    
    successful_models = [r for r in results if r["status"] == "success"]
    
    if successful_models:
        print(f"\n✓ {len(successful_models)} models working successfully:")
        
        # Sort by generation time (fastest first)
        successful_models.sort(key=lambda x: x["generation_time"])
        
        for result in successful_models:
            print(f"\n  Model: {result['model'].split('/')[-1]}")
            print(f"  Description: {result['description']}")
            print(f"  Load time: {result['load_time']:.2f}s")
            print(f"  Generation time: {result['generation_time']:.2f}s")
            print(f"  File size: {result['file_size']:,} bytes")
            
        print(f"\n🏆 FASTEST MODEL: {successful_models[0]['model'].split('/')[-1]}")
        print(f"   Generation time: {successful_models[0]['generation_time']:.2f}s")
        
        # Find largest file (usually indicates better quality)
        largest_file = max(successful_models, key=lambda x: x["file_size"])
        print(f"\n🎤 HIGHEST QUALITY (largest file): {largest_file['model'].split('/')[-1]}")
        print(f"   File size: {largest_file['file_size']:,} bytes")
        
    print(f"\n✗ {len([r for r in results if r['status'] != 'success'])} models had issues:")
    for result in results:
        if result["status"] != "success":
            print(f"  - {result['model'].split('/')[-1]}: {result['status']}")
    
    print("\n" + "="*80)
    print("RECOMMENDATIONS FOR BEST OFFLINE TTS:")
    print("="*80)
    
    if successful_models:
        fastest = successful_models[0]
        print(f"1. FASTEST: {fastest['model']}")
        print(f"   - Use for real-time applications")
        print(f"   - {fastest['generation_time']:.2f}s generation time")
        
        if len(successful_models) > 1:
            highest_quality = max(successful_models, key=lambda x: x["file_size"])
            print(f"\n2. HIGHEST QUALITY: {highest_quality['model']}")
            print(f"   - Use for best audio quality")
            print(f"   - {highest_quality['file_size']:,} bytes file size")
        
        print(f"\n3. RECOMMENDED: Use VITS or Tacotron2 for best balance of speed and quality")
    else:
        print("No models working - check installation or try different models")

def main():
    print("=== COQUI TTS COMPREHENSIVE TESTING ===")
    print(f"Python version: {sys.version}")
    print(f"Working directory: {os.getcwd()}")
    
    results = test_coqui_tts_models()
    print_results_summary(results)
    
    return len([r for r in results if r["status"] == "success"]) > 0

if __name__ == "__main__":
    success = main()
    sys.exit(0 if success else 1)