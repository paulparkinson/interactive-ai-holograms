# Dual Audio Output Setup Guide

This guide shows you how to route audio to both Unreal Live Link Hub and your speakers simultaneously.

## Step 1: Install VB-Audio Virtual Cable

1. **Download VB-CABLE** from: https://vb-audio.com/Cable/
2. Extract the ZIP file
3. Right-click `VBCABLE_Setup_x64.exe` → **Run as Administrator**
4. Click **"Install Driver"**
5. **Restart your computer**

## Step 2: Verify Installation

1. Right-click the **speaker icon** in system tray → **Sound settings**
2. Click **"More sound settings"** (or search for "Sound Control Panel")
3. In the **Playback** tab, you should see:
   - Your existing speakers/headphones (keep as default)
   - **CABLE Input (VB-Audio Virtual Cable)** ← New virtual device

## Step 3: Configure Unreal Engine 5.6 Live Link Hub

1. Open **Unreal Engine** and your **Live Link Hub**
2. In Live Link Hub audio settings, set the **audio input source** to:
   - **"CABLE Output (VB-Audio Virtual Cable)"**
3. This will capture audio sent to the virtual cable

## Step 4: Test Audio Devices

Run your Java application and check the console output. It will print:

```
=== Available Audio Output Devices ===
0: Primary Sound Driver - Direct Sound Driver
1: Speakers (Realtek High Definition Audio) - Direct Sound Driver  
2: CABLE Input (VB-Audio Virtual Cable) - Direct Sound Driver  ← Use this name
3: ... other devices ...
=====================================
```

Note the exact name containing "CABLE"

## Step 5: Update Your Code

The code has been updated to route audio to different outputs:

```java
// When audio2FaceEnabled is FALSE:
// 1st playback → VB-Audio Virtual Cable (for Unreal)
TTSAndAudio2Face.playAudioFileToDevice(filename, "CABLE");

// 800ms delay
Thread.sleep(800);

// 2nd playback → Default speakers (for you to hear)
TTSAndAudio2Face.playAudioFile(filename);
```

Now update `AIHoloController.java` to use `playAudioFileToDevice` for the first playback.

## Step 6: How It Works

```
Your Java App
    ↓
    ├─→ 1st Playback → CABLE Input (Virtual Cable)
    │                      ↓
    │                  CABLE Output → Unreal Live Link Hub → Metahuman lipsync
    │
    ├─→ 800ms delay
    │
    └─→ 2nd Playback → Your Speakers → You hear it
```

## Troubleshooting

**Problem**: "Device containing 'CABLE' not found"
- **Solution**: Check the exact device name in the console output and update the device name string

**Problem**: No audio in Unreal
- **Solution**: Make sure Live Link Hub is listening to "CABLE Output (VB-Audio Virtual Cable)"

**Problem**: Hear echo/double audio
- **Solution**: The 800ms delay might be too short or too long. Adjust in code.

**Problem**: Want to test without Unreal
- **Solution**: Use Windows "Sound" settings → Right-click "CABLE Input" → "Test" to hear if audio is being routed

## Alternative: VoiceMeeter (More Advanced)

If you want more control, use **VoiceMeeter Banana** (free) instead:
- https://vb-audio.com/Voicemeeter/banana.htm
- Allows routing multiple audio sources to multiple outputs with mixing
- Can delay audio streams if needed
- More complex but more powerful

