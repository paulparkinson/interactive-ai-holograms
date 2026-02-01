package oracleai.aiholo;

import org.springframework.stereotype.Component;

/**
 * Centralized configuration class for all application settings.
 * All configuration values are read from environment variables or system properties,
 * with sensible defaults where applicable.
 */
@Component
public class Configuration {
    
    // ========== Database Configuration ==========
    public static String getDbUser() {
        return System.getenv("DB_USER");
    }
    
    public static String getDbPassword() {
        return System.getenv("DB_PASSWORD");
    }
    
    public static String getDbUrl() {
        return System.getenv("DB_URL");
    }
    
    // ========== Voice Assistant Configuration ==========
    public static boolean isVoiceAssistantEnabled() {
        return "true".equalsIgnoreCase(System.getenv("ENABLE_VOICE_ASSISTANT"));
    }
    
    /**
     * Get the voice assistant engine to use (porcupine or openwakeword)
     * @return "porcupine" or "openwakeword", defaults to "porcupine"
     */
    public static String getVoiceAssistantEngine() {
        String engine = System.getenv("VOICE_ASSISTANT_ENGINE");
        return engine != null ? engine.toLowerCase() : "porcupine";
    }
    
    // Porcupine-specific configuration
    public static String getPorcupineAccessKey() {
        return System.getenv("PORCUPINE_ACCESS_KEY");
    }
    
    public static String getKeywordPath() {
        return System.getenv("KEYWORD_PATH");
    }
    
    // OpenWakeWord-specific configuration
    public static String getOpenWakeWordScriptPath() {
        String scriptPath = System.getenv("OPENWAKEWORD_SCRIPT_PATH");
        return scriptPath != null ? scriptPath : "wakeupwords/openwakeword_bridge.py";
    }
    
    public static String getOpenWakeWordModel() {
        String model = System.getenv("OPENWAKEWORD_MODEL");
        return model != null ? model : "hey_jarvis";
    }
    
    public static int getAudioDeviceIndex() {
        String value = System.getenv("AUDIO_DEVICE_INDEX");
        return value != null ? Integer.parseInt(value) : -1;
    }
    
    public static boolean isLanguageDetectionEnabled() {
        return !"false".equalsIgnoreCase(System.getenv("ENABLE_LANGUAGE_DETECTION"));
    }
    
    /**
     * Get the voice gender to use for TTS (MALE or FEMALE)
     * Checks runtime override first, then falls back to environment variable
     * @return "MALE" or "FEMALE", defaults to "FEMALE"
     */
    public static String getVoiceGender() {
        // Check if there's a runtime override (set via REST API)
        String runtimeGender = getRuntimeVoiceGender();
        if (runtimeGender != null) {
            return runtimeGender;
        }
        
        // Fall back to environment variable
        String gender = System.getenv("VOICE_GENDER");
        if (gender != null) {
            gender = gender.toUpperCase();
            if ("MALE".equals(gender) || "FEMALE".equals(gender)) {
                return gender;
            }
        }
        return "FEMALE"; // Default to female voice
    }
    
    /**
     * Get runtime voice gender override (set via REST API)
     * This is accessed via reflection to avoid circular dependency
     * @return runtime gender override or null
     */
    private static String getRuntimeVoiceGender() {
        try {
            Class<?> controllerClass = Class.forName("oracleai.aiholo.AIHoloController");
            java.lang.reflect.Field field = controllerClass.getDeclaredField("runtimeVoiceGender");
            field.setAccessible(true);
            return (String) field.get(null);
        } catch (Exception e) {
            // Field not accessible or doesn't exist - return null
            return null;
        }
    }
    
    // ========== Audio Output Configuration ==========
    public static String getAudioDeviceA() {
        return getEnvOrDefault("AUDIO_DEVICE_A", "CABLE Input (VB-Audio Virtual Cable)");
    }
    
    public static String getAudioDeviceB() {
        return getEnvOrDefault("AUDIO_DEVICE_B", "Speakers (2- Axiim Link)");
    }
    
    public static int getAudioDelayMs() {
        String value = System.getenv("AUDIO_DELAY_MS");
        return value != null ? Integer.parseInt(value) : 500;
    }
    
    public static boolean isEnableDualAudioOutput() {
        return !"false".equalsIgnoreCase(System.getenv("ENABLE_DUAL_AUDIO_OUTPUT"));
    }
    
    public static String getResponseLanguage() {
        String value = System.getenv("RESPONSE_LANGUAGE");
        return value != null ? value.toLowerCase() : "both";
    }
    
    // ========== OpenAI Configuration ==========
    public static String getOpenAiApiKey() {
        return System.getenv("OPENAI_API_KEY");
    }
    
    public static String getOpenAiKey() {
        return System.getenv("OPENAI_KEY");
    }
    
    public static String getOpenAiModel() {
        return getEnvOrDefault("OPENAI_MODEL", "gpt-4");
    }
    
    // ========== OCI Configuration ==========
    public static String getCompartmentId() {
        return System.getenv("COMPARTMENT_ID");
    }
    
    public static String getObjectStorageNamespace() {
        return System.getenv("OBJECTSTORAGE_NAMESPACE");
    }
    
    public static String getObjectStorageBucketName() {
        return System.getenv("OBJECTSTORAGE_BUCKETNAME");
    }
    
    public static String getOrdsEndpointUrl() {
        return System.getenv("ORDS_ENDPOINT_URL");
    }
    
    public static String getOrdsOmlOpsEndpointUrl() {
        String ordsUrl = getOrdsEndpointUrl();
        return ordsUrl != null ? ordsUrl + "/omlopsuser/" : null;
    }
    
    public static String getOciVisionServiceEndpoint() {
        return System.getenv("OCI_VISION_SERVICE_ENDPOINT");
    }
    
    public static String getOciVisionEndpoint() {
        return System.getenv("OCI_VISION_ENDPOINT");
    }
    
    public static String getOciCompartmentId() {
        return System.getenv("OCI_COMPARTMENT_ID");
    }
    
    public static String getOciApiKey() {
        return System.getenv("OCI_API_KEY");
    }
    
    public static String getOciConfigFile() {
        return System.getenv("OCICONFIG_FILE");
    }
    
    public static String getOciConfigProfile() {
        return System.getenv("OCICONFIG_PROFILE");
    }
    
    // ========== Sandbox/AI Optimizer Configuration ==========
    public static String getSandboxApiUrl() {
        return System.getenv("SANDBOX_API_URL");
    }
    
    public static String getAiOptimizer() {
        return System.getenv("AI_OPTIMZER");
    }
    
    // ========== Langflow Configuration ==========
    public static String getLangflowServerUrl() {
        return System.getenv("LANGFLOW_SERVER_URL");
    }
    
    public static String getLangflowFlowId() {
        return System.getenv("LANGFLOW_FLOW_ID");
    }
    
    public static String getLangflowApiKey() {
        return System.getenv("LANGFLOW_API_KEY");
    }
    
    // ========== TTS Configuration ==========
    public static String getTtsEngine() {
        String engine = System.getenv("TTS_ENGINE");
        return engine != null ? engine.toUpperCase() : "GCP";
    }
    
    public static String getTtsQuality() {
        return getEnvOrDefault("TTS_QUALITY", "BALANCED");
    }
    
    public static String getPythonPath() {
        String path = System.getenv("PYTHON_PATH");
        if (path != null) {
            return path;
        }
        return "C:\\Users\\" + System.getProperty("user.name") + "\\AppData\\Local\\Programs\\Python\\Python311\\python.exe";
    }
    
    public static boolean isCoquiTtsVerbose() {
        return Boolean.parseBoolean(getEnvOrDefault("COQUI_TTS_VERBOSE", "false"));
    }
    
    // ========== Audio and File Paths ==========
    public static String getAudioDirPath() {
        return System.getenv("AUDIO_DIR_PATH");
    }
    
    public static String getOutputFilePath() {
        return System.getenv("OUTPUT_FILE_PATH");
    }
    
    public static String getStatusFilePath() {
        return System.getenv("STATUS_FILE_PATH");
    }
    
    public static String getTempDir() {
        return System.getProperty("java.io.tmpdir");
    }
    
    // ========== Agentic AI Training Set Configuration ==========
    /**
     * Get the path to the Oracle + DoN Agentic AI Training Set file.
     * If not set or empty, default agents will operate without specialized training data.
     * Can be a classpath resource (e.g., "oracle-navy-training-set.txt") or absolute path.
     * @return Path to training set file, or null if not configured
     */
    public static String getAgenticTrainingSetPath() {
        return System.getenv("AGENTIC_TRAINING_SET_PATH");
    }
    
    // ========== Host and URL Configuration ==========
    public static String getAiholoHostUrl() {
        return System.getenv("AIHOLO_HOST_URL");
    }
    
    public static String getDigitalDoublesImagesEndpoint() {
        return System.getenv("DIGITAL_DOUBLES_IMAGES_ENDPOINT");
    }
    
    public static String getThreeDKey() {
        return System.getenv("THREED_KEY");
    }
    
    // ========== Feature Flags ==========
    public static boolean isAudio2FaceEnabled() {
        return Boolean.parseBoolean(System.getenv("IS_AUDIO2FACE"));
    }
    
    public static boolean isRedirectAnswerEnabled() {
        return Boolean.parseBoolean(getEnvOrDefault("REDIRECT_ANSWER", "false"));
    }
    
    public static boolean isPollForAnswersEnabled() {
        return Boolean.parseBoolean(getEnvOrDefault("POLL_FOR_ANSWERS", "false"));
    }
    
    // ========== Polling Configuration ==========
    public static String getPollServerUrl() {
        return getEnvOrDefault("POLL_SERVER_URL", "https://localhost:443");
    }
    
    public static String getPollUsername() {
        return getEnvOrDefault("POLL_USERNAME", "oracleai");
    }
    
    public static String getPollPassword() {
        return getEnvOrDefault("POLL_PASSWORD", "oracleai");
    }
    
    public static int getPollIntervalMs() {
        return Integer.parseInt(getEnvOrDefault("POLL_INTERVAL_MS", "1000"));
    }
    
    // ========== Remote API Poller Configuration ==========
    public static String getRemoteApiUrl() {
        return System.getenv("REMOTE_API_URL");
    }
    
    public static String getRemoteApiUser() {
        return System.getenv("REMOTE_API_USER");
    }
    
    public static String getRemoteApiPassword() {
        return System.getenv("REMOTE_API_PASSWORD");
    }
    
    // ========== Spring Cloud Configuration ==========
    public static boolean isSpringCloudOciEnabled() {
        return !"false".equalsIgnoreCase(System.getenv("SPRING_CLOUD_OCI_ENABLED"));
    }
    
    // ========== Helper Methods ==========
    private static String getEnvOrDefault(String key, String defaultValue) {
        String value = System.getenv(key);
        return value != null ? value : defaultValue;
    }
    
    /**
     * Validates that all required configuration is present.
     * @param requireDatabase whether database configuration is required
     * @return true if all required config is present, false otherwise
     */
    public static boolean validateConfiguration(boolean requireDatabase) {
        boolean valid = true;
        
        if (requireDatabase) {
            if (getDbUser() == null || getDbPassword() == null || getDbUrl() == null) {
                System.err.println("ERROR: Database configuration missing. Set DB_USER, DB_PASSWORD, and DB_URL.");
                valid = false;
            }
        }
        
        if (isVoiceAssistantEnabled()) {
            if (getPorcupineAccessKey() == null || getPorcupineAccessKey().isEmpty()) {
                System.err.println("ERROR: PORCUPINE_ACCESS_KEY not set but voice assistant is enabled.");
                valid = false;
            }
            if (getKeywordPath() == null || getKeywordPath().isEmpty()) {
                System.err.println("ERROR: KEYWORD_PATH not set but voice assistant is enabled.");
                valid = false;
            }
        }
        
        return valid;
    }
    
    /**
     * Prints current configuration (excluding sensitive values).
     */
    public static void printConfiguration() {
        System.out.println("\n========== Application Configuration ==========");
        System.out.println("Voice Assistant Enabled: " + isVoiceAssistantEnabled());
        System.out.println("Language Detection Enabled: " + isLanguageDetectionEnabled());
        System.out.println("Response Language: " + getResponseLanguage());
        System.out.println("TTS Engine: " + getTtsEngine());
        System.out.println("TTS Quality: " + getTtsQuality());
        System.out.println("Audio Device A: " + getAudioDeviceA());
        System.out.println("Audio Device B: " + getAudioDeviceB());
        System.out.println("Dual Audio Output Enabled: " + isEnableDualAudioOutput());
        System.out.println("Audio Delay (ms): " + getAudioDelayMs());
        System.out.println("Audio2Face Enabled: " + isAudio2FaceEnabled());
        System.out.println("Redirect Answer Enabled: " + isRedirectAnswerEnabled());
        System.out.println("Poll For Answers Enabled: " + isPollForAnswersEnabled());
        System.out.println("OpenAI Model: " + getOpenAiModel());
        System.out.println("Database Configured: " + (getDbUrl() != null));
        System.out.println("Langflow Configured: " + (getLangflowServerUrl() != null));
        System.out.println("============================================\n");
    }
}
