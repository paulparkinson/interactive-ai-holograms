package oracleai.aiholo;

import org.springframework.stereotype.Service;
import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;

/**
 * Centralized service for handling audio output with support for dual audio streaming.
 * Manages playback to two independent audio devices (e.g., Stream A → Unreal, Stream B → Zoom).
 */
@Service
public class AudioOutputService {
    
    /**
     * Plays audio file to both configured audio devices with optional delay between streams.
     * Uses configuration from Configuration class for device names, delay, and dual output enable flag.
     * 
     * @param fileName The name of the audio file (without path)
     * @param audioDirectoryPath The directory path where the audio file is located
     */
    public void playAudioToDualOutputs(String fileName, String audioDirectoryPath) {
        String deviceA = Configuration.getAudioDeviceA();
        String deviceB = Configuration.getAudioDeviceB();
        boolean enableDualAudio = Configuration.isEnableDualAudioOutput();
        int delayMs = Configuration.getAudioDelayMs();
        
        playAudioToDualOutputs(fileName, audioDirectoryPath, deviceA, deviceB, enableDualAudio, delayMs);
    }
    
    /**
     * Plays audio file to specified audio devices with configurable delay.
     * 
     * @param fileName The name of the audio file (without path)
     * @param audioDirectoryPath The directory path where the audio file is located
     * @param deviceA Primary audio device (Stream A)
     * @param deviceB Secondary audio device (Stream B)
     * @param enableDualAudio Whether to enable dual audio output
     * @param delayMs Delay in milliseconds between Stream A and Stream B
     */
    public void playAudioToDualOutputs(String fileName, String audioDirectoryPath, 
                                       String deviceA, String deviceB, 
                                       boolean enableDualAudio, int delayMs) {
        // Play Java Stream A → Unreal (CABLE device)
        System.out.println("Playing audio to Device A (" + deviceA + "): " + fileName);
        TTSCoquiEnhanced.playAudioFileToDevice(fileName, deviceA);
        
        if (enableDualAudio) {
            // Wait for delay then play Java Stream B → Zoom (or secondary output)
            System.out.println("Dual audio enabled - waiting " + delayMs + "ms before playing to Device B (" + deviceB + ")");
            try { 
                Thread.sleep(delayMs); 
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            System.out.println("Now playing to Device B after " + delayMs + "ms delay");
            TTSCoquiEnhanced.playAudioFileToDevice(fileName, deviceB);
        } else {
            System.out.println("Dual audio disabled - only playing to Device A");
        }
    }
    
    /**
     * Plays raw audio bytes to both configured audio devices with optional delay.
     * Useful for real-time TTS audio that doesn't come from a file.
     * 
     * @param audioData Raw PCM audio bytes
     * @param format AudioFormat specification for the audio data
     */
    public void playRawAudioToDualOutputs(byte[] audioData, AudioFormat format) {
        String deviceA = Configuration.getAudioDeviceA();
        String deviceB = Configuration.getAudioDeviceB();
        boolean enableDualAudio = Configuration.isEnableDualAudioOutput();
        int delayMs = Configuration.getAudioDelayMs();
        
        // Play to Device A
        System.out.println("Playing raw audio to Device A (" + deviceA + ")");
        playRawAudioToDevice(audioData, format, deviceA);
        
        if (enableDualAudio) {
            // Wait for delay then play to Device B
            System.out.println("Dual audio enabled - waiting " + delayMs + "ms before playing to Device B (" + deviceB + ")");
            try { 
                Thread.sleep(delayMs); 
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            System.out.println("Now playing to Device B after " + delayMs + "ms delay");
            playRawAudioToDevice(audioData, format, deviceB);
        } else {
            System.out.println("Dual audio disabled - only playing to Device A");
        }
    }
    
    /**
     * Plays raw audio bytes to a specific device.
     * 
     * @param audioData Raw PCM audio bytes
     * @param format AudioFormat specification for the audio data
     * @param deviceName Name (or partial name) of the audio device to play to
     */
    private void playRawAudioToDevice(byte[] audioData, AudioFormat format, String deviceName) {
        new Thread(() -> {
            try {
                Mixer.Info[] mixers = AudioSystem.getMixerInfo();
                Mixer targetMixer = null;
                
                // Find mixer matching the device name
                for (Mixer.Info mixerInfo : mixers) {
                    if (mixerInfo.getName().toLowerCase().contains(deviceName.toLowerCase())) {
                        Mixer mixer = AudioSystem.getMixer(mixerInfo);
                        // Check if this mixer supports Clip as a source line (output)
                        Line.Info[] sourceLines = mixer.getSourceLineInfo();
                        boolean supportsClip = false;
                        for (Line.Info lineInfo : sourceLines) {
                            if (lineInfo.getLineClass().equals(Clip.class)) {
                                supportsClip = true;
                                break;
                            }
                        }
                        if (supportsClip) {
                            targetMixer = mixer;
                            System.out.println("Using audio device: " + mixerInfo.getName());
                            break;
                        }
                    }
                }
                
                if (targetMixer == null) {
                    System.err.println("Device containing '" + deviceName + "' not found or doesn't support audio output. Using default.");
                    playRawAudioToDefaultDevice(audioData, format);
                    return;
                }
                
                ByteArrayInputStream inputStream = new ByteArrayInputStream(audioData);
                AudioInputStream audioInputStream = new AudioInputStream(inputStream, format, audioData.length / format.getFrameSize());
                
                DataLine.Info info = new DataLine.Info(Clip.class, format);
                Clip clip = (Clip) targetMixer.getLine(info);
                clip.open(audioInputStream);
                
                // Set volume to maximum if supported
                if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                    FloatControl gainControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                    gainControl.setValue(gainControl.getMaximum());
                }
                
                clip.start();
                
                // Wait for playback to complete
                Thread.sleep(audioData.length * 1000L / (long)(format.getSampleRate() * format.getFrameSize()));
                clip.close();
                
            } catch (Exception e) {
                System.err.println("Error playing audio to device '" + deviceName + "': " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
    }
    
    /**
     * Plays raw audio bytes to the default audio device.
     * 
     * @param audioData Raw PCM audio bytes
     * @param format AudioFormat specification for the audio data
     */
    private void playRawAudioToDefaultDevice(byte[] audioData, AudioFormat format) {
        try {
            Clip clip = AudioSystem.getClip();
            ByteArrayInputStream inputStream = new ByteArrayInputStream(audioData);
            AudioInputStream audioInputStream = new AudioInputStream(inputStream, format, audioData.length / format.getFrameSize());
            
            clip.open(audioInputStream);
            clip.start();
            
            // Wait for playback to complete
            Thread.sleep(audioData.length * 1000L / (long)(format.getSampleRate() * format.getFrameSize()));
            clip.close();
        } catch (Exception e) {
            System.err.println("Error playing audio to default device: " + e.getMessage());
        }
    }
}
