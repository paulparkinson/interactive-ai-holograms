#!/usr/bin/env python3
"""
OpenWakeWord Integration Script for Java Voice Assistant
Listens for wake words using the openWakeWord library
Communicates with Java via stdin/stdout
"""

import sys
import numpy as np
from openwakeword.model import Model

def main():
    if len(sys.argv) < 2:
        print("ERROR: Model name required", file=sys.stderr)
        sys.exit(1)
    
    model_name = sys.argv[1]
    
    try:
        # Initialize OpenWakeWord model
        owwModel = Model(wakeword_models=[model_name])
        print("READY", flush=True)
        
        # Main loop - receive audio frames from Java
        while True:
            line = sys.stdin.readline()
            if not line:
                break
                
            line = line.strip()
            
            # Check for quit command
            if line == "QUIT":
                break
            
            try:
                # Parse audio data (comma-separated 16-bit PCM samples)
                audio_data = np.array([int(x) for x in line.split(',')], dtype=np.int16)
                
                # Convert to float32 normalized to [-1, 1]
                audio_float = audio_data.astype(np.float32) / 32768.0
                
                # Process with OpenWakeWord
                prediction = owwModel.predict(audio_float)
                
                # Check if wake word was detected (threshold of 0.5)
                detected = any(score > 0.5 for score in prediction.values())
                
                if detected:
                    print("DETECTED", flush=True)
                else:
                    print("SILENT", flush=True)
                    
            except Exception as e:
                print(f"ERROR: {e}", file=sys.stderr, flush=True)
                print("SILENT", flush=True)
                
    except Exception as e:
        print(f"FATAL: {e}", file=sys.stderr, flush=True)
        sys.exit(1)

if __name__ == "__main__":
    main()
