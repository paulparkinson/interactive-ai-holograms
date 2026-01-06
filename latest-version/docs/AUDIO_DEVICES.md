# Audio Device Configuration

## Available Audio Outputs on Your System

Based on the detected audio devices, here are your available audio outputs:

### Audio Devices
1. **CABLE Input (VB-Audio Virtual Cable)** - Virtual audio cable (configured as Stream A)
2. **CABLE Output (VB-Audio Virtual Cable)** - Virtual audio cable output
3. **Speakers (2- Axiim Link)** - Axiim Link speakers (configured as Stream B)
4. **LG HDR 4K (NVIDIA High Definition Audio)** - HDMI audio output
5. **Speakers (RODE NT-USB)** - USB microphone speaker output
6. **CABLE In 16ch (VB-Audio Virtual Cable)** - 16-channel virtual cable
7. **Microphone (RODE NT-USB)** - USB microphone input

## Current Configuration

The dual audio output is configured in the `.env` file:

```env
# Stream A → Unreal (Live Link Hub source)
AUDIO_DEVICE_A=CABLE Input (VB-Audio Virtual Cable)

# Stream B → Zoom (local speaker/secondary output)
AUDIO_DEVICE_B=Speakers (2- Axiim Link)
```

### How It Works

The application streams TTS audio to two outputs simultaneously:
- **Stream A (AUDIO_DEVICE_A)**: Sends audio to Unreal Engine via VB-Audio Virtual Cable for hologram lip sync
- **Stream B (AUDIO_DEVICE_B)**: Sends audio to your local speakers/Zoom for you and others to hear

## Changing Audio Devices

To change the audio devices, edit the `.env` file and set `AUDIO_DEVICE_A` and/or `AUDIO_DEVICE_B` to one of the device names listed above.

**Important**: The device name must match exactly as shown in the list above.

### Common Configurations

#### Option 1: VB-Audio Cable + Axiim Speakers (Current)
```env
AUDIO_DEVICE_A=CABLE Input (VB-Audio Virtual Cable)
AUDIO_DEVICE_B=Speakers (2- Axiim Link)
```

#### Option 2: VB-Audio Cable + RODE Speakers
```env
AUDIO_DEVICE_A=CABLE Input (VB-Audio Virtual Cable)
AUDIO_DEVICE_B=Speakers (RODE NT-USB)
```

#### Option 3: VB-Audio Cable + HDMI Output
```env
AUDIO_DEVICE_A=CABLE Input (VB-Audio Virtual Cable)
AUDIO_DEVICE_B=LG HDR 4K (NVIDIA High Definition Audio)
```

#### Option 4: Dual VB-Audio Cables
```env
AUDIO_DEVICE_A=CABLE Input (VB-Audio Virtual Cable)
AUDIO_DEVICE_B=CABLE Output (VB-Audio Virtual Cable)
```

## Testing Audio Devices

To verify audio device names on your system at any time, run:

```powershell
Get-PnpDevice -Class AudioEndpoint | Where-Object {$_.Status -eq "OK"} | Select-Object FriendlyName, InstanceId | Format-Table -AutoSize
```

## Troubleshooting

### Audio not playing
1. Verify the device names in `.env` match exactly (case-sensitive)
2. Check that the devices are enabled in Windows Sound settings
3. Ensure VB-Audio Virtual Cable is installed and configured
4. Restart the application after changing `.env` values

### Unreal Engine not receiving audio
1. Verify `AUDIO_DEVICE_A` is set to "CABLE Input (VB-Audio Virtual Cable)"
2. In Unreal Engine Live Link Hub, ensure the audio source is set to the VB-Audio Virtual Cable
3. Check VB-Audio Control Panel to verify the cable is active

### Zoom not receiving audio
1. In Zoom settings, set your microphone to the VB-Audio Virtual Cable
2. OR set your speaker to match `AUDIO_DEVICE_B` for direct output
