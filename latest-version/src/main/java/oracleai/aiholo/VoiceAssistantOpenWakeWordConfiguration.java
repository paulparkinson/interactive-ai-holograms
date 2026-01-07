package oracleai.aiholo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class VoiceAssistantOpenWakeWordConfiguration {

    @Autowired
    @Qualifier("openWakeWordVoiceAssistant")
    private VoiceAssistantOpenWakeWordService voiceAssistantOpenWakeWordService;

    /**
     * Initializes the OpenWakeWord Voice Assistant on Spring Boot application startup.
     */
    @Bean
    @ConditionalOnProperty(name = "voice.assistant.engine", havingValue = "openwakeword")
    public ApplicationRunner initializeOpenWakeWordVoiceAssistant() {
        return args -> {
            // Check if voice assistant should be enabled via centralized configuration
            if (oracleai.aiholo.Configuration.isVoiceAssistantEnabled()) {
                String engine = oracleai.aiholo.Configuration.getVoiceAssistantEngine();
                if (!"openwakeword".equalsIgnoreCase(engine)) {
                    System.out.println("OpenWakeWord Voice Assistant skipped (using " + engine + " engine)");
                    return;
                }
                
                System.out.println("\n================================");
                System.out.println("Starting OpenWakeWord Voice Assistant...");
                System.out.println("================================\n");

                // Get configuration from centralized Configuration class
                String pythonScriptPath = oracleai.aiholo.Configuration.getOpenWakeWordScriptPath();
                String modelName = oracleai.aiholo.Configuration.getOpenWakeWordModel();
                
                if (pythonScriptPath == null || pythonScriptPath.isEmpty()) {
                    System.err.println("ERROR: OPENWAKEWORD_SCRIPT_PATH environment variable not set");
                    return;
                }
                
                if (modelName == null || modelName.isEmpty()) {
                    System.err.println("ERROR: OPENWAKEWORD_MODEL environment variable not set");
                    modelName = "hey_jarvis";  // Default model
                }

                try {
                    // Configuration
                    int audioDeviceIndex = oracleai.aiholo.Configuration.getAudioDeviceIndex();

                    // Start the voice assistant
                    voiceAssistantOpenWakeWordService.runDemo(
                            pythonScriptPath,
                            modelName,
                            audioDeviceIndex
                    );
                } catch (Exception e) {
                    System.err.println("Error initializing OpenWakeWord Voice Assistant: " + e.getMessage());
                    e.printStackTrace();
                }
            } else {
                System.out.println("Voice Assistant is disabled. Set ENABLE_VOICE_ASSISTANT=true to enable.");
            }
        };
    }
}
