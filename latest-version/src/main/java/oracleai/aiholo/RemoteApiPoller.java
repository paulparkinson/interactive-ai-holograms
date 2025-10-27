package oracleai.aiholo;

import org.json.JSONObject;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Polls a remote API for TTS playback instructions and triggers audio generation when values change.
 * This class runs on a scheduled interval and monitors a remote endpoint for JSON updates containing
 * text-to-speech parameters.
 */
public class RemoteApiPoller {
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private static final String REMOTE_API_URL = System.getenv().getOrDefault("REMOTE_API_URL", "https://aiholo2.org/api/getValue");
    private static final String REMOTE_API_USER = System.getenv("REMOTE_API_USER");
    private static final String REMOTE_API_PASSWORD = System.getenv("REMOTE_API_PASSWORD");
    private static final AtomicReference<String> lastRemoteValue = new AtomicReference<>(null);

    /**
     * Start polling the remote API every 2 seconds. When the JSON value changes,
     * trigger TTS playback with the parameters from the JSON.
     */
    public static void startPolling() {
        System.out.println("Starting remote API polling: " + REMOTE_API_URL);
        scheduler.scheduleAtFixedRate(() -> {
            try {
                JSONObject currentJson = pollRemoteApi();
                if (currentJson != null) {
                    String currentValue = currentJson.toString();
                    String previousValue = lastRemoteValue.getAndSet(currentValue);
                    if (previousValue != null && !previousValue.equals(currentValue)) {
                        System.out.println("Remote value changed, triggering TTS");
                        triggerTtsForRemoteValue(currentJson);
                    } else if (previousValue == null) {
                        System.out.println("Initial remote value received");
                    }
                }
            } catch (Exception e) {
                System.err.println("Error polling remote API: " + e.getMessage());
            }
        }, 0, 2, TimeUnit.SECONDS);
    }

    /**
     * Poll the remote API with basic authentication and return the JSON response.
     */
    private static JSONObject pollRemoteApi() {
        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            
            // Set basic authentication
            String auth = REMOTE_API_USER + ":" + REMOTE_API_PASSWORD;
            byte[] encodedAuth = Base64.getEncoder().encode(auth.getBytes(StandardCharsets.UTF_8));
            String authHeader = "Basic " + new String(encodedAuth, StandardCharsets.UTF_8);
            headers.set("Authorization", authHeader);
            
            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(
                REMOTE_API_URL, 
                HttpMethod.GET, 
                entity, 
                String.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                String body = response.getBody().trim();
                return new JSONObject(body);
            }
        } catch (Exception e) {
            System.err.println("Failed to poll remote API: " + e.getMessage());
        }
        return null;
    }

    /**
     * Trigger TTS playback when the remote value changes.
     * Parses the JSON object and extracts all parameters for generateAndPlayTts.
     */
    private static void triggerTtsForRemoteValue(JSONObject jsonData) {
        new Thread(() -> {
            try {
                // Extract values from JSON
                String textToSay = jsonData.optString("textToSay", "");
                String languageCode = jsonData.optString("languageCode", "en-US");
                String voiceName = jsonData.optString("voiceName", AIHoloController.resolveFemaleVoice(languageCode));
                String aiPipelineLabel = jsonData.optString("aiPipelineLabel", "executeSandbox");
                double aiDurationMillis = jsonData.optDouble("aiDurationMillis", 0.0);
                // Use the global IS_AUDIO2FACE setting instead of the JSON value
                boolean audio2FaceEnabled = AIHoloController.isAudio2FaceEnabled();
                String ttsSelectionStr = jsonData.optString("ttsSelection", "GCP");
                
                // Parse TTS selection
                AIHoloController.TTSSelection ttsSelection;
                try {
                    ttsSelection = AIHoloController.TTSSelection.valueOf(ttsSelectionStr.toUpperCase());
                } catch (IllegalArgumentException e) {
                    System.out.println("Unknown ttsSelection '" + ttsSelectionStr + "', using GCP");
                    ttsSelection = AIHoloController.TTSSelection.GCP;
                }
                
                System.out.println("Triggering TTS: text='" + textToSay + "', lang=" + languageCode + 
                                   ", voice=" + voiceName + ", tts=" + ttsSelection + ", audio2face=" + audio2FaceEnabled);
                
                // Call the controller's generateAndPlayTts method
                // AIHoloController.generateAndPlayTtsStatic(
                //     "output.wav",
                //     textToSay,
                //     languageCode,
                //     voiceName,
                //     aiPipelineLabel,
                //     aiDurationMillis,
                //     ttsSelection,
                //     audio2FaceEnabled
                // );
            } catch (Exception e) {
                System.err.println("Error generating TTS for remote value: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
    }
}
