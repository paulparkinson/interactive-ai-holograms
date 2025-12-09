package oracleai.aiholo;

import org.json.JSONObject;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Polling client that checks a server for new answers and plays them locally.
 * Used when POLL_FOR_ANSWERS=true to create a client instance that polls
 * a server instance for new responses.
 */
public class PollingClient {
    
    private final String serverUrl;
    private final String username;
    private final String password;
    private final int pollIntervalMs;
    private final AtomicBoolean pollingActive = new AtomicBoolean(false);
    private final AIHoloController controller;
    
    private long lastProcessedTimestamp = 0;
    
    public PollingClient(String serverUrl, String username, String password, 
                        int pollIntervalMs, AIHoloController controller) {
        this.serverUrl = serverUrl;
        this.username = username;
        this.password = password;
        this.pollIntervalMs = pollIntervalMs;
        this.controller = controller;
    }
    
    /**
     * Start the polling client that checks the server for new answers
     */
    public void start() {
        if (pollingActive.compareAndSet(false, true)) {
            System.out.println("Starting polling client...");
            System.out.println("Poll Server URL: " + serverUrl);
            System.out.println("Poll Interval: " + pollIntervalMs + "ms");
            System.out.println("Username: " + username);
            
            CompletableFuture.runAsync(() -> {
                while (pollingActive.get()) {
                    try {
                        pollServerForAnswers();
                    } catch (Exception e) {
                        System.err.println("Error in polling client: " + e.getMessage());
                        // Continue polling even on errors
                    }
                    
                    try {
                        Thread.sleep(pollIntervalMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                System.out.println("Polling client stopped.");
            });
        }
    }
    
    /**
     * Stop the polling client
     */
    public void stop() {
        pollingActive.set(false);
        System.out.println("Stopping polling client...");
    }
    
    /**
     * Check if polling is currently active
     */
    public boolean isActive() {
        return pollingActive.get();
    }
    
    /**
     * Get the last processed timestamp
     */
    public long getLastProcessedTimestamp() {
        return lastProcessedTimestamp;
    }
    
    /**
     * Poll the server for new answers and play them if found
     */
    private void pollServerForAnswers() {
        try {
            // Create HTTP client request
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            
            // Add basic authentication
            String auth = username + ":" + password;
            byte[] encodedAuth = java.util.Base64.getEncoder().encode(auth.getBytes(StandardCharsets.UTF_8));
            String authHeader = "Basic " + new String(encodedAuth);
            headers.set("Authorization", authHeader);
            
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            // Poll the endpoint
            ResponseEntity<String> response = restTemplate.exchange(
                serverUrl + "/status/simple/get",
                HttpMethod.GET,
                entity,
                String.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK) {
                String responseBody = response.getBody();
                
                // Skip if response is "default" (no new data)
                if (responseBody != null && !responseBody.equals("default")) {
                    processPolledAnswer(responseBody);
                }
            } else {
                System.err.println("Polling failed with status: " + response.getStatusCode());
            }
            
        } catch (Exception e) {
            System.err.println("Error polling server: " + e.getMessage());
            // Don't spam logs for connection errors - continue polling
        }
    }
    
    /**
     * Process a polled answer from the server and play it locally
     */
    private void processPolledAnswer(String jsonResponse) {
        try {
            JSONObject data = new JSONObject(jsonResponse);
            
            // Check timestamp to avoid replaying same response
            long timestamp = data.optLong("timestamp", 0);
            if (timestamp <= lastProcessedTimestamp) {
                return; // Already processed this response
            }
            
            lastProcessedTimestamp = timestamp;
            
            // Extract answer data
            String textToSay = data.optString("textToSay", "");
            String languageCode = data.optString("languageCode", "en-US");
            String voiceName = data.optString("voiceName", "en-US-Chirp3-HD-Aoede");
            String aiPipelineLabel = data.optString("aiPipelineLabel", "Polled Answer");
            double aiDurationMillis = data.optDouble("aiDurationMillis", 0.0);
            String ttsSelectionName = data.optString("ttsSelection", "GCP");
            boolean audio2FaceEnabled = data.optBoolean("audio2FaceEnabled", false);
            
            // Display new response info
            System.out.println("\n" + "=".repeat(60));
            System.out.println("New Polled Response Received:");
            System.out.println("  Agent: " + aiPipelineLabel);
            System.out.println("  Language: " + languageCode);
            System.out.println("  Voice: " + voiceName);
            System.out.println("  TTS Engine: " + ttsSelectionName);
            System.out.println("  Duration: " + aiDurationMillis + " ms");
            System.out.println("  Text: " + textToSay);
            System.out.println("=".repeat(60));
            
            if (textToSay != null && !textToSay.trim().isEmpty()) {
                // Parse TTS selection
                AIHoloController.TTSSelection ttsSelection;
                try {
                    ttsSelection = AIHoloController.TTSSelection.valueOf(ttsSelectionName.toUpperCase());
                } catch (IllegalArgumentException e) {
                    ttsSelection = AIHoloController.TTSSelection.defaultSelection();
                }
                
                System.out.println("Playing polled answer locally...");
                
                // Call the controller method to play the answer
                controller.playPolledAnswer(textToSay, languageCode, voiceName, 
                    aiPipelineLabel, aiDurationMillis, ttsSelection, audio2FaceEnabled);
                
                System.out.println("Polled answer playback completed.");
            }
            
        } catch (Exception e) {
            System.err.println("Error processing polled answer: " + e.getMessage());
            e.printStackTrace();
        }
    }
}