package oracleai.aiholo;

import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import javax.sound.sampled.*;
import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Enhanced TTS with Coqui neural TTS integration
 * Provides high-quality offline neural text-to-speech using Coqui TTS
 * Falls back to Google Cloud TTS if needed
 */
public class TTSCoquiEnhanced {
    
    // Python 3.11 executable path
    private static final String PYTHON_311_PATH = "C:\\Users\\paulp\\AppData\\Local\\Programs\\Python\\Python311\\python.exe";
    
    // TTS script path
    private static final String COQUI_TTS_SCRIPT = "coqui_tts_integration.py";

    private static final boolean COQUI_VERBOSE_LOGS = Boolean.parseBoolean(
        System.getenv().getOrDefault("COQUI_TTS_VERBOSE", "false"));
    
    // TTS quality modes
    public enum TTSQuality {
        FAST("COQUI_MODEL_FAST", "fast"),
        BALANCED("COQUI_MODEL_BALANCED", "tts_models/en/ljspeech/tacotron2-DDC"),
        QUALITY("COQUI_MODEL_QUALITY", "tts_models/en/ljspeech/tacotron2-DDC");

        private final String overrideEnvVar;
        private final String defaultModel;

        TTSQuality(String overrideEnvVar, String defaultModel) {
            this.overrideEnvVar = overrideEnvVar;
            this.defaultModel = defaultModel;
        }

        public String getEffectiveModel() {
            String override = System.getenv(overrideEnvVar);
            if (override != null && !override.isBlank()) {
                return override;
            }
            return defaultModel;
        }
    }

    private static final ConcurrentMap<String, PlaybackMetrics> pendingPlaybackMetrics = new ConcurrentHashMap<>();

    public static void registerPlaybackMetrics(String fileName, PlaybackMetrics metrics) {
        if (fileName == null || metrics == null) {
            return;
        }
        pendingPlaybackMetrics.put(fileName, metrics);
    }

    static PlaybackMetrics consumePlaybackMetrics(String fileName) {
        if (fileName == null) {
            return null;
        }
        return pendingPlaybackMetrics.remove(fileName);
    }

    public static final class PlaybackMetrics {
        private final String pipelineLabel;
        private final double aiDurationMillis;
        private final Double ttsDurationMillis;
        private final boolean ttsSuccessful;
        private final String ttsEngineLabel;

        public PlaybackMetrics(String pipelineLabel, double aiDurationMillis, Double ttsDurationMillis,
                               boolean ttsSuccessful, String ttsEngineLabel) {
            this.pipelineLabel = (pipelineLabel == null || pipelineLabel.isBlank()) ? "Unknown pipeline" : pipelineLabel;
            this.aiDurationMillis = aiDurationMillis < 0 ? 0 : aiDurationMillis;
            this.ttsDurationMillis = ttsDurationMillis;
            this.ttsSuccessful = ttsSuccessful;
            this.ttsEngineLabel = (ttsEngineLabel == null || ttsEngineLabel.isBlank()) ? "Unknown TTS" : ttsEngineLabel;
        }

        public String formatForLog() {
            String ttsMessage;
            if (ttsDurationMillis == null) {
                ttsMessage = "not attempted";
            } else if (ttsSuccessful) {
                ttsMessage = String.format(Locale.ROOT, "%.2f ms", ttsDurationMillis);
            } else {
                ttsMessage = String.format(Locale.ROOT, "failed after %.2f ms", ttsDurationMillis);
            }
            return String.format(
                    Locale.ROOT,
                    "Timing -> %s: %.2f ms | TTS [%s]: %s",
                    pipelineLabel,
                    aiDurationMillis,
                    ttsEngineLabel,
                    ttsMessage);
        }
    }
    
    /**
     * Generate TTS using Coqui neural TTS
     */
    public static void generateCoquiTTS(String fileName, String text, TTSQuality quality,
                                        String languageCode, String voiceName) throws Exception {
        String fullPath = Paths.get(AIHoloController.AUDIO_DIR_PATH, fileName).toString();
        String safeLanguageCode = (languageCode == null || languageCode.isBlank()) ? "en-US" : languageCode;
        String safeVoiceName = voiceName != null ? voiceName : "";
        
        String effectiveModel = quality.getEffectiveModel();
        long processStartNs = System.nanoTime();

        ProcessBuilder pb = new ProcessBuilder(
            PYTHON_311_PATH,
            COQUI_TTS_SCRIPT,
            "--text", text,
            "--output", fullPath,
            "--model", effectiveModel,
            "--lang", safeLanguageCode
        );

        if (!safeVoiceName.isBlank()) {
            pb.command().add("--voice");
            pb.command().add(safeVoiceName);
        }

        // Ensure UTF-8 encoding so Coqui logs with unicode characters don't break on Windows code pages
        Map<String, String> environment = pb.environment();
        environment.put("PYTHONUTF8", "1");
        environment.put("PYTHONIOENCODING", "utf-8");
        environment.putIfAbsent("LC_ALL", "en_US.UTF-8");
        environment.putIfAbsent("LANG", "en_US.UTF-8");
        
        pb.directory(new File("."));
        pb.redirectErrorStream(true);
        
        try {
            Process process = pb.start();
            
            // Read output for debugging
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            boolean suppressionNoted = false;
            StringBuilder output = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
                if (shouldLogCoquiLine(line)) {
                    System.out.println("Coqui TTS: " + line);
                } else if (!suppressionNoted && !COQUI_VERBOSE_LOGS) {
                    System.out.println("Coqui TTS: (additional Coqui output suppressed — set COQUI_TTS_VERBOSE=true for full logs)");
                    suppressionNoted = true;
                } else if (COQUI_VERBOSE_LOGS) {
                    System.out.println("Coqui TTS: " + line);
                }
            }
            
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                double elapsedSeconds = (System.nanoTime() - processStartNs) / 1_000_000_000.0;
                System.out.printf(Locale.ROOT,
                        "Coqui TTS finished in %.2fs using model %s. Audio saved to: %s%n",
                        elapsedSeconds,
                        effectiveModel,
                        fullPath);
            } else {
                throw new RuntimeException("Coqui TTS failed with exit code: " + exitCode + "\nOutput: " + output.toString());
            }
            
        } catch (Exception e) {
            System.err.println("Error running Coqui TTS: " + e.getMessage());
            throw e;
        }
    }

    private static boolean shouldLogCoquiLine(String line) {
        if (COQUI_VERBOSE_LOGS) {
            return true;
        }
        if (line == null || line.isBlank()) {
            return false;
        }
        String lower = line.toLowerCase(Locale.ROOT);
        return lower.contains("error")
                || lower.contains("fail")
                || lower.contains("warning")
                || lower.contains("success")
                || lower.contains("processing time")
                || lower.contains("real-time factor");
    }
    
    /**
     * Main TTS method with fallback strategy
     * 1. Try Coqui TTS (offline, high quality)
     * 2. Fall back to Google Cloud TTS
     * 3. Fall back to Python pyttsx3
     */
    public static void TTS(String fileName, String text, String languageCode, String voicename) throws Exception {
        System.out.println("Enhanced TTS - generating audio for: " + text);
        
        // Determine quality based on environment or default to BALANCED
        String qualityEnv = System.getenv("TTS_QUALITY");
        TTSQuality quality = TTSQuality.BALANCED; // Default
        
        if (qualityEnv != null) {
            try {
                quality = TTSQuality.valueOf(qualityEnv.toUpperCase());
            } catch (IllegalArgumentException e) {
                System.out.println("Invalid TTS_QUALITY environment variable, using BALANCED");
            }
        }
        
        try {
            // Try Coqui TTS first (best quality offline)
            String modelInUse = quality.getEffectiveModel();
            System.out.println("Attempting Coqui TTS with quality: " + quality +
                " (model: " + modelInUse + ")" + ", languageCode: " + languageCode +
                ", voiceName: " + voicename);
            generateCoquiTTS(fileName, text, quality, languageCode, voicename);
            
        } catch (Exception coquiError) {
            System.err.println("Coqui TTS failed: " + coquiError.getMessage());
            
            try {
                // Fallback to Google Cloud TTS
                System.out.println("Falling back to Google Cloud TTS");
                TTSAndAudio2Face.TTS(fileName, text, languageCode, voicename);
                
            } catch (Exception googleError) {
                System.err.println("Google Cloud TTS failed: " + googleError.getMessage());
                
                try {
                    // Final fallback to Python pyttsx3
                    System.out.println("Falling back to Python pyttsx3");
                    generatePythonTTS(fileName, text, "pyttsx3");
                    
                } catch (Exception pythonError) {
                    System.err.println("All TTS methods failed. Last error: " + pythonError.getMessage());
                    throw new RuntimeException("All TTS methods failed", pythonError);
                }
            }
        }
    }
    
    /**
     * Fallback Python TTS (pyttsx3 or gTTS)
     */
    private static void generatePythonTTS(String fileName, String text, String engine) throws Exception {
        String fullPath = Paths.get(AIHoloController.AUDIO_DIR_PATH, fileName).toString();
        
        ProcessBuilder pb = new ProcessBuilder(
            "cmd", "/c",
            "py python_tts.py",
            "--text", text,
            "--output", fullPath,
            "--engine", engine
        );
        
        pb.directory(new File("."));
        pb.redirectErrorStream(true);
        
        Process process = pb.start();
        
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        while ((line = reader.readLine()) != null) {
            System.out.println("Python TTS: " + line);
        }
        
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Python TTS failed with exit code: " + exitCode);
        }
    }
    
    /**
     * Audio2Face integration (same as original)
     */
    public static void sendToAudio2Face(String fileName) {
        if ("true".equalsIgnoreCase(System.getenv("IS_AUDIO2FACE"))) {
            System.out.print("sendToAudio2Face for fileName:" + fileName + " ...");
            RestTemplate restTemplate = new RestTemplate();
            String baseUrl = "http://localhost:8011/A2F/Player/";

            String setRootPathUrl = baseUrl + "SetRootPath";
            Map<String, Object> rootPathPayload = new HashMap<>();
            rootPathPayload.put("a2f_player", "/World/audio2face/Player");
            rootPathPayload.put("dir_path", AIHoloController.AUDIO_DIR_PATH);
            sendPostRequest(restTemplate, setRootPathUrl, rootPathPayload);

            String setTrackUrl = baseUrl + "SetTrack";
            Map<String, Object> trackPayload = new HashMap<>();
            trackPayload.put("a2f_player", "/World/audio2face/Player");
            trackPayload.put("file_name", fileName);
            trackPayload.put("time_range", new int[] { 0, -1 });
            sendPostRequest(restTemplate, setTrackUrl, trackPayload);

            String playTrackUrl = baseUrl + "Play";
            Map<String, Object> playPayload = new HashMap<>();
            playPayload.put("a2f_player", "/World/audio2face/Player");
            sendPostRequest(restTemplate, playTrackUrl, playPayload);
            System.out.println(" ...sendToAudio2Face complete");
        }
    }

    private static void sendPostRequest(RestTemplate restTemplate, String url, Map<String, Object> payload) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
        if (!response.getStatusCode().is2xxSuccessful()) {
            System.err.println("Failed to send request to " + url + ". Response: " + response.getBody());
        }
    }

    /**
     * Enhanced audio playback with better error handling
     */
    public static void playAudioFile(String filename) {
        new Thread(() -> {
            try {
                if (AIHoloController.AUDIO_DIR_PATH == null) {
                    System.err.println("AUDIO_DIR_PATH environment variable is not set");
                    return;
                }
                
                Path audioPath = Paths.get(AIHoloController.AUDIO_DIR_PATH, filename);
                String fullPath = audioPath.toString();
                File audioFile = audioPath.toFile();
                
                System.out.println("Attempting to play: " + fullPath);
                System.out.println("File exists: " + audioFile.exists());
                System.out.println("File size: " + audioFile.length() + " bytes");
                
                if (!audioFile.exists()) {
                    System.err.println("Audio file not found: " + fullPath);
                    return;
                }
                
                System.out.println("Playing audio file on local machine: " + fullPath);
                
                AudioInputStream audioStream = AudioSystem.getAudioInputStream(audioFile);
                AudioFormat format = audioStream.getFormat();
                System.out.println("Audio format: " + format);
                
                Clip clip = AudioSystem.getClip();
                clip.open(audioStream);
                
                // Set volume to maximum (if supported)
                if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                    FloatControl gainControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                    gainControl.setValue(gainControl.getMaximum());
                    System.out.println("Set volume to maximum: " + gainControl.getValue());
                }
                
                PlaybackMetrics metrics = consumePlaybackMetrics(filename);
                clip.start();
                if (metrics != null) {
                    System.out.println(metrics.formatForLog());
                }
                System.out.println("Audio playback started, duration: " + (clip.getMicrosecondLength() / 1000000.0) + " seconds");
                
                // Wait for the audio to finish playing
                Thread.sleep(clip.getMicrosecondLength() / 1000);
                
                clip.close();
                audioStream.close();
                
                System.out.println("Finished playing audio file: " + filename);
                
            } catch (Exception e) {
                System.err.println("Error playing audio file: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
    }
    
    /**
     * Test method to verify Coqui TTS is working
     */
    public static boolean testCoquiTTS() {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "cmd", "/c",
                PYTHON_311_PATH + " " + COQUI_TTS_SCRIPT + " --test"
            );
            
            pb.directory(new File("."));
            Process process = pb.start();
            
            int exitCode = process.waitFor();
            return exitCode == 0;
            
        } catch (Exception e) {
            System.err.println("Coqui TTS test failed: " + e.getMessage());
            return false;
        }
    }
}