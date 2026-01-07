package oracleai.aiholo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ai.picovoice.porcupine.Porcupine;

@Configuration
public class VoiceAssistantPorcupineConfiguration {

    @Autowired
    @Qualifier("porcupineVoiceAssistant")
    private VoiceAssistantPorcupineService voiceAssistantPorcupineService;

    /**
     * Initializes the Porcupine Voice Assistant on Spring Boot application startup.
     */
    @Bean
    @ConditionalOnProperty(name = "voice.assistant.engine", havingValue = "porcupine", matchIfMissing = true)
    public ApplicationRunner initializePorcupineVoiceAssistant() {
        return args -> {
            // Check if voice assistant should be enabled via centralized configuration
            if (oracleai.aiholo.Configuration.isVoiceAssistantEnabled()) {
                String engine = oracleai.aiholo.Configuration.getVoiceAssistantEngine();
                if (!"porcupine".equalsIgnoreCase(engine)) {
                    System.out.println("Porcupine Voice Assistant skipped (using " + engine + " engine)");
                    return;
                }
                
                System.out.println("\n================================");
                System.out.println("Starting Porcupine Voice Assistant...");
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
                    voiceAssistantPorcupineService.runDemo(
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
