package oracleai.aiholo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ai.picovoice.porcupine.Porcupine;

@Configuration
public class VoiceAssistantConfiguration {

    @Autowired
    private VoiceAssistantService voiceAssistantService;

    /**
     * Initializes the Voice Assistant on Spring Boot application startup.
     * Comment out the @Bean annotation below to disable voice assistant startup.
     */
    @Bean
    public ApplicationRunner initializeVoiceAssistant() {
        return args -> {
            // Check if voice assistant should be enabled via centralized configuration
            if (oracleai.aiholo.Configuration.isVoiceAssistantEnabled()) {
                System.out.println("\n================================");
                System.out.println("Starting Voice Assistant...");
                System.out.println("================================\n");

                // Get configuration from centralized Configuration class
                String accessKey = oracleai.aiholo.Configuration.getPorcupineAccessKey();
                String keywordPath = oracleai.aiholo.Configuration.getKeywordPath();
                
                if (accessKey == null || accessKey.isEmpty()) {
                    System.err.println("ERROR: PORCUPINE_ACCESS_KEY environment variable not set");
                    return;
                }
                
                if (keywordPath == null || keywordPath.isEmpty()) {
                    System.err.println("ERROR: KEYWORD_PATH environment variable not set");
                    return;
                }

                try {
                    // Configuration
                    String[] keywordPaths = {keywordPath};
                    float[] sensitivities = {0.5f};
                    int audioDeviceIndex = oracleai.aiholo.Configuration.getAudioDeviceIndex();
                    String device = "best";

                    // Start the voice assistant
                    voiceAssistantService.runDemo(
                            accessKey,
                            Porcupine.LIBRARY_PATH,
                            Porcupine.MODEL_PATH,
                            device,
                            keywordPaths,
                            sensitivities,
                            audioDeviceIndex
                    );
                } catch (Exception e) {
                    System.err.println("Error initializing Voice Assistant: " + e.getMessage());
                    e.printStackTrace();
                }
            } else {
                System.out.println("Voice Assistant is disabled. Set ENABLE_VOICE_ASSISTANT=true to enable.");
            }
        };
    }
}
