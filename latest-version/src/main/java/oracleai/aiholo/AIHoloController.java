package oracleai.aiholo;

import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CompletableFuture;

import oracleai.common.GetSetController;

import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.oracle.CreateOption;
import dev.langchain4j.store.embedding.oracle.EmbeddingTable;
import dev.langchain4j.store.embedding.oracle.Index;
import dev.langchain4j.store.embedding.oracle.OracleEmbeddingStore;
import org.json.JSONObject;
//import org.springframework.ai.document.Document;
//import org.springframework.ai.vectorstore.SearchRequest;
//import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import com.google.cloud.texttospeech.v1.AudioEncoding;
import com.google.cloud.texttospeech.v1.SsmlVoiceGender;
import com.google.cloud.texttospeech.v1.SynthesisInput;
import com.google.cloud.texttospeech.v1.SynthesizeSpeechResponse;
import com.google.cloud.texttospeech.v1.TextToSpeechClient;
import com.google.cloud.texttospeech.v1.VoiceSelectionParams;
import com.google.protobuf.ByteString;
import com.google.cloud.texttospeech.v1.AudioConfig;

import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.PostConstruct;
import javax.sql.*;
import java.nio.file.Path;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Locale;
import java.util.Map;

import org.springframework.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import oracleai.aiholo.agents.*;
import java.util.*;

@Controller
@RequestMapping("/aiholo")
// @CrossOrigin(origins = "*")
public class AIHoloController {
    
    @Autowired
    private AgentService agentService;
    
    @Autowired
    private ChatGPTService chatGPTService;
    
    @Autowired
    private AudioOutputService audioOutputService;
    
    @Autowired
    private AgentStateService agentStateService;
    
    private String theValue = "mirrorme";
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private static final String SANDBOX_API_URL = Configuration.getSandboxApiUrl();
    private static final String AI_OPTIMZER = Configuration.getAiOptimizer();
    private static final String OPENAI_KEY = Configuration.getOpenAiKey();
    private static final String OCI_VISION_ENDPOINT = Configuration.getOciVisionEndpoint();
    private static final String OCI_COMPARTMENT_ID = Configuration.getOciCompartmentId();
    private static final String OCI_API_KEY = Configuration.getOciApiKey();
    // Langflow API configuration. These environment variables should be set
    // when deploying the application. LANGFLOW_SERVER_URL is the base URL for
    // the Langflow server (for example, http://host:7860/api), FLOW_ID is the
    // ID of the flow you wish to call, and LANGFLOW_API_KEY is your API key.
    private static final String LANGFLOW_SERVER_URL = Configuration.getLangflowServerUrl();
    private static final String LANGFLOW_FLOW_ID = Configuration.getLangflowFlowId();
    private static final String LANGFLOW_API_KEY = Configuration.getLangflowApiKey();
    static final String AUDIO_DIR_PATH = Configuration.getAudioDirPath();
    private static final String OUTPUT_FILE_PATH = Configuration.getOutputFilePath();
    private static final String AIHOLO_HOST_URL = Configuration.getAiholoHostUrl();
    private static final AtomicBoolean AUDIO2FACE_ENABLED =
            new AtomicBoolean(Configuration.isAudio2FaceEnabled());
    private static final boolean REDIRECT_ANSWER = Configuration.isRedirectAnswerEnabled();
    private static final boolean POLL_FOR_ANSWERS = Configuration.isPollForAnswersEnabled();
    private static final String POLL_SERVER_URL = Configuration.getPollServerUrl();
    private static final String POLL_USERNAME = Configuration.getPollUsername();
    private static final String POLL_PASSWORD = Configuration.getPollPassword();
    private static final int POLL_INTERVAL_MS = Configuration.getPollIntervalMs();
    private static boolean enableIntroAudio = false; // Set to false to disable random intro sounds
    
    // Polling client instance
    private static PollingClient pollingClient;

    // Agent management now handled by AgentService
    // Static initialization removed - agents managed by injected AgentService

    // Audio2Face support removed - always play locally
    static boolean isAudio2FaceEnabled() {
        return false; // Always disabled
    }

    static void setAudio2FaceEnabled(boolean enabled) {
        // No-op: Audio2Face removed
        System.out.println("Audio2Face is no longer supported - audio always plays locally");
    }
    
    @PostMapping("/config/docSimilarityThreshold")
    @ResponseBody
    public String setDocSimilarityThreshold(@RequestParam("threshold") double threshold) {
        if (threshold < 0.0 || threshold > 1.0) {
            return "Error: Threshold must be between 0.0 and 1.0";
        }
        oracleai.aiholo.agents.OracleDocAgent.setSimilarityThreshold(threshold);
        return "Document similarity threshold set to: " + threshold;
    }
    
    // Voice gender runtime storage (for session-based override)
    private static volatile String runtimeVoiceGender = null;
    
    @PostMapping("/config/voiceGender")
    @ResponseBody
    public String setVoiceGender(@RequestParam("gender") String gender) {
        String normalized = gender == null ? "" : gender.toUpperCase().trim();
        if (!"MALE".equals(normalized) && !"FEMALE".equals(normalized)) {
            return "Error: Gender must be 'MALE' or 'FEMALE'";
        }
        runtimeVoiceGender = normalized;
        System.out.println("Voice gender set to: " + normalized);
        return "Voice gender set to: " + normalized + " (affects new TTS requests)";
    }
    
    @GetMapping("/config/voiceGender")
    @ResponseBody
    public String getVoiceGender() {
        String gender = runtimeVoiceGender != null ? runtimeVoiceGender : Configuration.getVoiceGender();
        return "{\"voiceGender\": \"" + gender + "\", \"source\": \"" + 
               (runtimeVoiceGender != null ? "runtime" : "environment") + "\"}";
    }
    
    // TTS Engine Configuration - GCP is the default for reliability
    private static final String TTS_ENGINE = Configuration.getTtsEngine();
    
    // TTS Engine Options
    public enum TTSEngine {
        GCP,        // Google Cloud TTS (current default)
        OCI,        // Oracle Cloud Infrastructure TTS (placeholder)
        COQUI       // Coqui TTS (high-quality offline neural TTS)
    }

    public enum TTSSelection {
        COQUI_FAST("Coqui Fast (lower latency)", TTSEngine.COQUI, TTSCoquiEnhanced.TTSQuality.FAST),
        COQUI_BALANCED("Coqui Balanced", TTSEngine.COQUI, TTSCoquiEnhanced.TTSQuality.BALANCED),
        COQUI_QUALITY("Coqui Quality", TTSEngine.COQUI, TTSCoquiEnhanced.TTSQuality.QUALITY),
        GCP("Google Cloud TTS", TTSEngine.GCP, null),
        OCI("Oracle Cloud TTS", TTSEngine.OCI, null);

        private final String displayLabel;
        private final TTSEngine engine;
        private final TTSCoquiEnhanced.TTSQuality coquiQuality;

        TTSSelection(String displayLabel, TTSEngine engine, TTSCoquiEnhanced.TTSQuality coquiQuality) {
            this.displayLabel = displayLabel;
            this.engine = engine;
            this.coquiQuality = coquiQuality;
        }

        public String getDisplayLabel() {
            return displayLabel;
        }

        public TTSEngine getEngine() {
            return engine;
        }

        public TTSCoquiEnhanced.TTSQuality getCoquiQuality() {
            return coquiQuality;
        }

        public static TTSSelection fromParam(String raw) {
            if (raw == null || raw.isBlank()) {
                return COQUI_BALANCED;
            }
            try {
                return TTSSelection.valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                System.out.println("Unknown ttsMode='" + raw + "', defaulting to COQUI_BALANCED");
                return COQUI_BALANCED;
            }
        }

        public static TTSSelection fallbackFor(TTSSelection selection) {
            if (selection == null) {
                return GCP;
            }
            switch (selection) {
                case COQUI_FAST:
                case COQUI_QUALITY:
                    return COQUI_BALANCED;
                case COQUI_BALANCED:
                    return GCP;
                case GCP:
                    // If GCP fails, don't fall back to broken systems - return null to trigger error
                    return null;
                case OCI:
                    return GCP;
                default:
                    return GCP;
            }
        }

        public static TTSSelection defaultSelection() {
            return GCP;
        }
    }
    
    private static final TTSEngine ACTIVE_TTS_ENGINE;
    
    static {
        try {
            ACTIVE_TTS_ENGINE = TTSEngine.valueOf(TTS_ENGINE);
            System.out.println("TTS Engine initialized: " + ACTIVE_TTS_ENGINE);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Invalid TTS_ENGINE: " + TTS_ENGINE + 
                ". Valid options: GCP, OCI, COQUI", e);
        }
    }
    
    /**
     * Gets the TTS mode string for frontend based on Configuration settings.
     * Maps TTS_ENGINE + TTS_QUALITY to frontend TTS mode values.
     */
    private String getTtsModeString() {
        switch (ACTIVE_TTS_ENGINE) {
            case GCP:
                return "GCP";
            case OCI:
                return "OCI";
            case COQUI:
                // Map quality to frontend options
                String quality = Configuration.getTtsQuality();
                if ("FAST".equalsIgnoreCase(quality)) {
                    return "COQUI_FAST";
                } else if ("BALANCED".equalsIgnoreCase(quality)) {
                    return "COQUI_BALANCED";
                } else if ("QUALITY".equalsIgnoreCase(quality)) {
                    return "COQUI_QUALITY";
                } else {
                    return "COQUI_BALANCED"; // default
                }
            default:
                return "GCP";
        }
    }
    
    private static int currentAnswerIntro = 0;
    private static String aiholo_prompt_additions = "";

    static {
        // Check for aiholo_prompt_additions.txt in AUDIO_DIR_PATH at startup
        if (AUDIO_DIR_PATH != null) {
            try {
                java.nio.file.Path additionsPath = Path.of(AUDIO_DIR_PATH, "aiholo_prompt_additions.txt");
                if (Files.exists(additionsPath)) {
                    aiholo_prompt_additions = new String(Files.readAllBytes(additionsPath), StandardCharsets.UTF_8).trim();
                    System.out.println("Loaded aiholo_prompt_additions: " + aiholo_prompt_additions);
                }
            } catch (Exception e) {
                System.err.println("Could not load aiholo_prompt_additions.txt: " + e.getMessage());
            }
        }
    }

    @SuppressWarnings("unused")
    private static final String DEFAULT_LANGUAGE_CODE = "es-ES";
    @SuppressWarnings("unused")
    private static final String DEFAULT_VOICE_NAME = "es-ES-Wavenet-D";
    private final static String sql = """
                SELECT DBMS_CLOUD_AI.GENERATE(
                    prompt       => ?,
                    profile_name => 'VIDEOGAMES_PROFILE',
                    action       => ?
                ) FROM dual
            """;

    @Autowired(required = false)
    private DataSource dataSource;

    @SuppressWarnings("unused")
    private static final Object metahumanLock = new Object();
    private static boolean isRecentQuestionProcessed;
    @SuppressWarnings("unused")
    private static String languageCode = "es";

    private static final String DEFAULT_FEMALE_VOICE = "en-US-Chirp3-HD-Aoede";
    private static final Map<String, String> FEMALE_VOICE_MAP = Map.ofEntries(
            Map.entry("EN-US", "en-US-Chirp3-HD-Aoede"),
            Map.entry("EN-GB", "en-GB-Wavenet-A"),
            Map.entry("EN-AU", "en-AU-Wavenet-A"),
            Map.entry("ES-ES", "es-ES-Wavenet-D"),
            Map.entry("ES-MX", "es-US-Wavenet-A"),
            Map.entry("PT-BR", "pt-BR-Wavenet-D"),
            Map.entry("FR-FR", "fr-FR-Wavenet-A"),
            Map.entry("DE-DE", "de-DE-Wavenet-A"),
            Map.entry("IT-IT", "it-IT-Wavenet-A"),
            Map.entry("RO-RO", "ro-RO-Wavenet-A"),
            Map.entry("ZH-SG", "cmn-CN-Wavenet-A"),
            Map.entry("ZH-CN", "cmn-CN-Wavenet-A"),
            Map.entry("JA-JP", "ja-JP-Wavenet-A"),
            Map.entry("HI-IN", "hi-IN-Wavenet-A"),
            Map.entry("HE-IL", "he-IL-Wavenet-A"),
            Map.entry("AR-AE", "ar-AE-Wavenet-A"),
            Map.entry("GA-GA", "ga-GA-Wavenet-A"),
            Map.entry("GA-IE", "ga-GA-Wavenet-A")
    );

    private static final String DEFAULT_MALE_VOICE = "en-US-Polyglot-1";
    private static final Map<String, String> MALE_VOICE_MAP = Map.ofEntries(
            Map.entry("EN-US", "en-US-Polyglot-1"),
            Map.entry("EN-GB", "en-GB-Wavenet-B"),
            Map.entry("EN-AU", "en-AU-Wavenet-B"),
            Map.entry("ES-ES", "es-ES-Wavenet-B"),
            Map.entry("ES-MX", "es-US-Wavenet-B"),
            Map.entry("PT-BR", "pt-BR-Wavenet-B"),
            Map.entry("FR-FR", "fr-FR-Wavenet-B"),
            Map.entry("DE-DE", "de-DE-Wavenet-B"),
            Map.entry("IT-IT", "it-IT-Wavenet-C"),
            Map.entry("RO-RO", "ro-RO-Standard-B"),
            Map.entry("ZH-SG", "cmn-CN-Wavenet-B"),
            Map.entry("ZH-CN", "cmn-CN-Wavenet-B"),
            Map.entry("JA-JP", "ja-JP-Wavenet-C"),
            Map.entry("HI-IN", "hi-IN-Wavenet-B"),
            Map.entry("HE-IL", "he-IL-Wavenet-B"),
            Map.entry("AR-AE", "ar-AE-Wavenet-B"),
            Map.entry("GA-GA", "ga-GA-Standard-A"),
            Map.entry("GA-IE", "ga-GA-Standard-A")
    );

    public static String resolveFemaleVoice(String languageCode) {
        String normalized = languageCode == null ? "" : languageCode.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return DEFAULT_FEMALE_VOICE;
        }
        return FEMALE_VOICE_MAP.getOrDefault(normalized, DEFAULT_FEMALE_VOICE);
    }

    public static String resolveMaleVoice(String languageCode) {
        String normalized = languageCode == null ? "" : languageCode.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return DEFAULT_MALE_VOICE;
        }
        return MALE_VOICE_MAP.getOrDefault(normalized, DEFAULT_MALE_VOICE);
    }

    /**
     * Resolve voice name based on language code and gender from configuration
     * @param languageCode The language code (e.g., "en-US", "es-ES")
     * @return The appropriate voice name for the language and configured gender
     */
    public static String resolveVoice(String languageCode) {
        String gender = Configuration.getVoiceGender();
        return resolveVoice(languageCode, gender);
    }

    /**
     * Resolve voice name based on language code and specified gender
     * @param languageCode The language code (e.g., "en-US", "es-ES")
     * @param gender "MALE" or "FEMALE"
     * @return The appropriate voice name for the language and gender
     */
    public static String resolveVoice(String languageCode, String gender) {
        if ("MALE".equalsIgnoreCase(gender)) {
            return resolveMaleVoice(languageCode);
        } else {
            return resolveFemaleVoice(languageCode);
        }
    }

    public AIHoloController() {
        // Constructor - inactivity monitor and RemoteApiPoller have been disabled
        
        // Start polling client if enabled
        if (POLL_FOR_ANSWERS && !REDIRECT_ANSWER) {
            startPollingClient();
        }
    }
    
    /**
     * Start the polling client that checks the server for new answers and plays them locally
     */
    private void startPollingClient() {
        if (pollingClient == null) {
            pollingClient = new PollingClient(POLL_SERVER_URL, POLL_USERNAME, POLL_PASSWORD, POLL_INTERVAL_MS, this);
            pollingClient.start();
        }
    }
    
    /**
     * Play a polled answer locally using existing TTS infrastructure
     * Called by PollingClient when a new answer is received
     */
    public void playPolledAnswer(String textToSay, String languageCode, String voiceName,
                                String aiPipelineLabel, double aiDurationMillis, TTSSelection ttsSelection,
                                boolean audio2FaceEnabled) {
        try {
            String fileName = "polled_output.wav";
            
            // Generate and play TTS using existing method (audio config from Configuration class)
            generateAndPlayTts(fileName, textToSay, languageCode, voiceName,
                aiPipelineLabel, aiDurationMillis, ttsSelection, audio2FaceEnabled);
                
        } catch (Exception e) {
            System.err.println("Error playing polled answer: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Stop the polling client
     */
    public void stopPollingClient() {
        if (pollingClient != null) {
            pollingClient.stop();
        }
    }

    /**
     * Register agents that require Spring-managed dependencies (DataSource, ChatGPTService)
     * This runs after Spring completes dependency injection
     */
    // Agent registration now handled by AgentService
    // OracleDocAgent is automatically registered with DataSource and ChatGPTService dependencies

    @SuppressWarnings("unused")
    private void startInactivityMonitor() {
        System.out.println("startInactivityMonitor DISABLED - no more 15-minute explainer audio");
        // DISABLED: This was playing explainer audio every 15 minutes causing interruptions
        // scheduler.scheduleAtFixedRate(() -> {
        //     if (isRecentQuestionProcessed) {
        //         System.out.println("isRecentQuestionProcessed true so skipping the timecheck/keepalive");
        //         isRecentQuestionProcessed = false;
        //     }
        //     if (isAudio2FaceEnabled()) {
        //         TTSCoquiEnhanced.sendToAudio2Face("explainer.wav");
        //     } else {
        //         TTSCoquiEnhanced.playAudioFileToDevice("explainer.wav", "CABLE");
        //         if (enableDualAudioOutput) {
        //             try { Thread.sleep(audioDelayMs); } catch (InterruptedException e) { }
        //             TTSCoquiEnhanced.playAudioFile("explainer.wav");
        //         }
        //     }
        // }, 1, 15, TimeUnit.MINUTES);
    }


    @GetMapping("")
    public String home(@RequestParam(value = "languageCode", defaultValue = "en-US") String languageCode, Model model) {
        System.out.println("AIHolo root languageCode = " + languageCode);
        AIHoloController.languageCode = languageCode;
        model.addAttribute("languageCode", languageCode);
        model.addAttribute("aiholoHostUrl", AIHOLO_HOST_URL != null ? AIHOLO_HOST_URL : "http://localhost:8080");
        String resolvedVoice = resolveVoice(languageCode);
        model.addAttribute("voiceName", resolvedVoice);
        
        // Add dual audio configuration attributes from Configuration class
        model.addAttribute("audioDelayMs", Configuration.getAudioDelayMs());
        model.addAttribute("enableDualAudioOutput", Configuration.isEnableDualAudioOutput());
        model.addAttribute("audioDeviceA", Configuration.getAudioDeviceA());
        model.addAttribute("audioDeviceB", Configuration.getAudioDeviceB());
        
        // Add TTS mode from Configuration (maps TTS_ENGINE + TTS_QUALITY from .env)
        String ttsMode = getTtsModeString();
        model.addAttribute("ttsMode", ttsMode);
        
        return "aiholo";
    }


    @GetMapping("/explainer")
    @ResponseBody
    public String explainer(@RequestParam(value = "enableDualAudio", required = false, defaultValue = "false") Boolean enableDualAudio,
                           @RequestParam(value = "audioDeviceA", required = false, defaultValue = "CABLE Input (VB-Audio Virtual Cable)") String audioDeviceA,
                           @RequestParam(value = "audioDeviceB", required = false, defaultValue = "Speakers (VB-Audio Voicemeeter VAIO)") String audioDeviceB,
                           @RequestParam(value = "audioDelayMs", required = false, defaultValue = "500") Integer audioDelayMs) throws Exception {
        System.out.println("AIHoloController.explainer - enableDualAudio: " + enableDualAudio + ", audioDelayMs: " + audioDelayMs);
        theValue = "financialagent"; // Write same value as FinancialAgent for consistency
        agentStateService.writeAgentValue(theValue);
        
        // Use AudioOutputService for dual audio playback
        System.out.println("EXPLAINER: Playing explainer.wav via AudioOutputService");
        audioOutputService.playAudioToDualOutputs("explainer.wav", AUDIO_DIR_PATH);


        return "Explained";
    }


    @GetMapping("/play")
    @ResponseBody
    public String play(@RequestParam("question") String question,
                       @RequestParam("languageCode") String languageCode,
                       @RequestParam("voiceName") String voicename,
                       @RequestParam(value = "ttsMode", required = false) String ttsModeParam,
                       @RequestParam(value = "llmModel", required = false, defaultValue = "gpt-4") String llmModel,
                       @RequestParam(value = "audioDelayMs", required = false, defaultValue = "500") Integer audioDelayMs,
                       @RequestParam(value = "enableDualAudio", required = false) Boolean enableDualAudio,
                       @RequestParam(value = "audioDeviceA", required = false) String audioDeviceAParam,
                       @RequestParam(value = "audioDeviceB", required = false) String audioDeviceBParam) throws Exception {
        System.out.println(
                "play question: " + question +
                        " languageCode:" + languageCode + " voicename:" + voicename);
        System.out.println("modified question: " + question);
        String resolvedVoiceName = resolveVoice(languageCode);
        if (voicename == null || !voicename.equals(resolvedVoiceName)) {
            System.out.println("Overriding requested voice with resolved voice (" + Configuration.getVoiceGender() + "): " + resolvedVoiceName);
        }
        voicename = resolvedVoiceName;
        TTSSelection ttsSelection = TTSSelection.fromParam(ttsModeParam);
        System.out.println("Requested TTS mode: " + ttsSelection.name());
        
        // Note: Audio configuration parameters (audioDelayMs, enableDualAudio, audioDeviceA/B) 
        // are now configured via .env file and loaded through Configuration class
        // Request parameters are deprecated but kept for backward compatibility logging
        if (audioDelayMs != null) {
            System.out.println("WARNING: audioDelayMs parameter is deprecated. Configure via AUDIO_DELAY_MS in .env file. Requested: " + audioDelayMs + "ms, Using: " + Configuration.getAudioDelayMs() + "ms");
        }
        if (enableDualAudio != null) {
            System.out.println("WARNING: enableDualAudio parameter is deprecated. Configure via ENABLE_DUAL_AUDIO_OUTPUT in .env file. Requested: " + enableDualAudio + ", Using: " + Configuration.isEnableDualAudioOutput());
        }
        if (audioDeviceAParam != null && !audioDeviceAParam.trim().isEmpty()) {
            System.out.println("WARNING: audioDeviceA parameter is deprecated. Configure via AUDIO_DEVICE_A in .env file. Requested: " + audioDeviceAParam + ", Using: " + Configuration.getAudioDeviceA());
        }
        if (audioDeviceBParam != null && !audioDeviceBParam.trim().isEmpty()) {
            System.out.println("WARNING: audioDeviceB parameter is deprecated. Configure via AUDIO_DEVICE_B in .env file. Requested: " + audioDeviceBParam + ", Using: " + Configuration.getAudioDeviceB());
        }
        
        final boolean audio2FaceEnabled = false; // Audio2Face removed - always false
        theValue = "question";
        agentStateService.writeAgentValue(theValue);
        
        String filePath = OUTPUT_FILE_PATH != null ? OUTPUT_FILE_PATH : "aiholo_output.txt";
        if (false) { // Legacy code block - keeping for reference but disabled
            try (FileWriter writer = new FileWriter(filePath)) {
                JSONObject json = new JSONObject();
                json.put("data", theValue);
                writer.write(json.toString());
                writer.flush();
            } catch (IOException e) {
                return "Error writing to file: " + e.getMessage();
            }
        }
            String normalized = question.toLowerCase();

        // DISABLED: Intro audio was causing overlapping audio loops  
        // This thread was playing intro sounds that overlapped with main response audio
        // Both intro and main response were playing to CABLE + speakers = 4 audio streams total
        System.out.println("Intro audio disabled to prevent overlapping streams");
        
        /*
            try {
                // languagecode:es-MX voicename:es-US-Wavenet-A
                if (languageCode.equals("es-MX")) {
                    if (audio2FaceEnabled) {
                        TTSAndAudio2Face.sendToAudio2Face("tts-es-USFEMALEes-US-Wavenet-A_¡Claro!¡U.wav");
                    } else {
                        // Play audio for Unreal Live Link Hub source (first output)
                        TTSAndAudio2Face.playAudioFileToDevice("tts-es-USFEMALEes-US-Wavenet-A_¡Claro!¡U.wav", "CABLE");
                        // Wait ~800ms then play to local speaker (second output)
                        try { Thread.sleep(audioDelayMs); } catch (InterruptedException e) { }
                        TTSAndAudio2Face.playAudioFile("tts-es-USFEMALEes-US-Wavenet-A_¡Claro!¡U.wav");
                    }
                } else {
                    // Switch for currentAnswerIntro
                    switch (currentAnswerIntro) {
                        case 0:
                            if (audio2FaceEnabled) {
                                TTSAndAudio2Face.sendToAudio2Face("tts-en-USFEMALEAoede_Sure!Illcheck.wav");
                            } else {
                                // Play audio for Unreal Live Link Hub source (first output)
                                TTSAndAudio2Face.playAudioFileToDevice("tts-en-USFEMALEAoede_Sure!Illcheck.wav", "CABLE");
                                // Wait ~800ms then play to local speaker (second output)
                                try { Thread.sleep(audioDelayMs); } catch (InterruptedException e) { }
                                TTSAndAudio2Face.playAudioFile("tts-en-USFEMALEAoede_Sure!Illcheck.wav");
                            }
                            break;
                        case 1:
                            if (audio2FaceEnabled) {
                                TTSAndAudio2Face.sendToAudio2Face("tts-en-USFEMALEAoede_on_it.wav");
                            } else {
                                // Play audio for Unreal Live Link Hub source (first output)
                                TTSAndAudio2Face.playAudioFileToDevice("tts-en-USFEMALEAoede_on_it.wav", "CABLE");
                                // Wait ~800ms then play to local speaker (second output)
                                try { Thread.sleep(audioDelayMs); } catch (InterruptedException e) { }
                                TTSAndAudio2Face.playAudioFile("tts-en-USFEMALEAoede_on_it.wav");
                            }
                            break;
                        case 2:
                            if (audio2FaceEnabled) {
                                TTSAndAudio2Face.sendToAudio2Face("tts-en-USFEMALEAoede_one_sec.wav");
                            } else {
                                // Play audio for Unreal Live Link Hub source (first output)
                                TTSAndAudio2Face.playAudioFileToDevice("tts-en-USFEMALEAoede_one_sec.wav", "CABLE");
                                // Wait ~800ms then play to local speaker (second output)
                                try { Thread.sleep(audioDelayMs); } catch (InterruptedException e) { }
                                TTSAndAudio2Face.playAudioFile("tts-en-USFEMALEAoede_one_sec.wav");
                            }
                            break;
                        case 3:
                            if (audio2FaceEnabled) {
                                TTSAndAudio2Face.sendToAudio2Face("tts-en-USFEMALEAoede_hmm.wav");
                            } else {
                                // Play audio for Unreal Live Link Hub source (first output)
                                TTSAndAudio2Face.playAudioFileToDevice("tts-en-USFEMALEAoede_hmm.wav", "CABLE");
                                // Wait ~800ms then play to local speaker (second output)
                                try { Thread.sleep(audioDelayMs); } catch (InterruptedException e) { }
                                TTSAndAudio2Face.playAudioFile("tts-en-USFEMALEAoede_hmm.wav");
                            }
                            break;
                        default:
                            if (audio2FaceEnabled) {
                                TTSAndAudio2Face.sendToAudio2Face("tts-en-USFEMALEAoede_Sure!Illcheck.wav");
                            } else {
                                // Play audio for Unreal Live Link Hub source (first output)
                                TTSAndAudio2Face.playAudioFile("tts-en-USFEMALEAoede_Sure!Illcheck.wav");
                                // Wait ~800ms then play to local speaker (second output)
                                try { Thread.sleep(audioDelayMs); } catch (InterruptedException e) { }
                                TTSAndAudio2Face.playAudioFile("tts-en-USFEMALEAoede_Sure!Illcheck.wav");
                            }
                    }
                    currentAnswerIntro++;
                    if (currentAnswerIntro > 3) currentAnswerIntro = 0;
                }
            } catch (Exception e) {
                System.err.println("Error in sendToAudio2Face: " + e.getMessage());
            }
        }).start();
        */

    // Check if the command is simply "stop" - if so, stop audio playback
    String trimmedQuestion = question.trim().toLowerCase();
    if (trimmedQuestion.equals("stop") || trimmedQuestion.equals("stop please") || 
        trimmedQuestion.equals("please stop")) {
        System.out.println("Stop command detected - stopping audio playback");
        audioOutputService.stopAllAudio();
        return "Audio stopped";
    }
    
    String action = "chat";
    String answer;
    double aiDurationMillis = 0.0;
    String aiPipelineLabel = "Unknown pipeline";
        if (languageCode.equals("pt-BR")) answer = "Desculpe. Não consegui encontrar uma resposta no banco de dados";
        else if (languageCode.equals("es-ES"))
            answer = "Lo siento, no pude encontrar una respuesta en la base de datos.";
        else if (languageCode.equals("en-GB")) answer = "Sorry, I couldn't find an answer in the database.";
        else if (languageCode.equals("zh-SG")) answer = "抱歉，我在数据库中找不到答案";
        else answer = "I'm sorry. I couldn't find an answer in the database";
        
        if (true) {
            // Use AgentService to process the question
            String processedQuestion = question.replace("use vectorrag", "").trim();
            processedQuestion += ". Respond in 25 words or less. " + aiholo_prompt_additions;
            
            long aiStartNs = System.nanoTime();
            AgentService.AgentResponse agentResponse = agentService.processQuestion(question, 25);
            aiDurationMillis = (System.nanoTime() - aiStartNs) / 1_000_000.0;
            
            if (agentResponse != null) {
                aiPipelineLabel = agentResponse.getAgentName();
                answer = agentResponse.getAnswer();
                theValue = agentResponse.getValueName();
                
                System.out.println("---------AGENT USED: " + agentResponse.getAgentName() + " writing value: " + theValue + " to file");
                agentStateService.writeAgentResponse(agentResponse);
            }

        } else {
            if (true) {
                action = "narrate";
                question = question.replace("use narrate", "").trim();
            } else {
                question = question.replace("use chat", "").trim();
            }
            question += ". Respond in 25 words or less. " + aiholo_prompt_additions;
            aiPipelineLabel = "Select AI/NL2SQL";
            long aiStartNs = System.nanoTime();
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
                System.out.println("Database Connection : " + connection);
                String response = null;
                preparedStatement.setString(1, question);
                preparedStatement.setString(2, action);
                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    if (resultSet.next()) {
                        response = resultSet.getString(1); // Retrieve AI response from the first column
                    }
                }
                answer = response;
                aiDurationMillis = (System.nanoTime() - aiStartNs) / 1_000_000.0;
            } catch (SQLException e) {
                aiDurationMillis = (System.nanoTime() - aiStartNs) / 1_000_000.0;
                System.err.println("Failed to connect to the database: " + e.getMessage());
                return "Database Connection Failed!";
            }
        }
        String fileName = "output.wav";

        // Strip out any "A:", "A2:", "A3:", etc. at the beginning of the answer string
        if (answer != null) {
            answer = answer.replaceFirst("^A\\d*:\\s*", "");
        }

        System.out.println("about to TTS and sendAudioToAudio2Face for answer: " + answer);
        try {
            if (REDIRECT_ANSWER) {
                executeRedirect(answer, languageCode, voicename, aiPipelineLabel, aiDurationMillis, ttsSelection, audio2FaceEnabled);
            } else {
                // Audio configuration now comes from Configuration class (loaded from .env)
                generateAndPlayTts(fileName, answer, languageCode, voicename, aiPipelineLabel, aiDurationMillis, ttsSelection, audio2FaceEnabled);
            }
        } catch (Exception e) {
            System.err.println("Requested TTS mode failed completely: " + e.getMessage());
            // Fallback to original implementation
            processMetahuman(fileName, answer, languageCode, voicename, audio2FaceEnabled);
        }
        return answer;
    }


    /**
     * curl -X 'POST' \
     * 'http://host/v1/chat/completions?client=server' \
     * -H 'accept: application/json' \
     * -H 'Authorization: Bearer bearer' \
     * -H 'Content-Type: application/json' \
     * -d '{
     * "messages": [
     * {
     * "role": "user",
     * "content": "What are Alternative Dispute Resolution"
     * }
     * ]
     * }'
     */

    /**
     * DEPRECATED: This method has been replaced by the AIToolkitAgent class.
     * The agent registration system now handles AI Toolkit/Sandbox queries.
     * 
     * @deprecated Use the AIToolkitAgent in the agent registration system instead
     */
    @Deprecated
    public String executeSandbox(String cummulativeResult) {
        System.out.println("using AI sandbox: " + cummulativeResult);
        Map<String, Object> payload = new HashMap<>();
        Map<String, String> message = new HashMap<>();
        message.put("role", "user");
        message.put("content", cummulativeResult);
        payload.put("messages", new Object[]{message});
        JSONObject jsonPayload = new JSONObject(payload);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", AI_OPTIMZER);
        headers.set("Accept", "application/json");
        headers.set("client", "server");
        HttpEntity<String> request = new HttpEntity<>(jsonPayload.toString(), headers);
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<String> response = restTemplate.exchange(SANDBOX_API_URL, HttpMethod.POST, request, String.class);
        String latestAnswer;
        if (response.getStatusCode() == HttpStatus.OK) {
            JSONObject responseData = new JSONObject(response.getBody());
            latestAnswer = responseData.getJSONArray("choices").getJSONObject(0).getJSONObject("message")
                    .getString("content");
            System.out.println("RAG Full Response latest_answer: " + latestAnswer);
            return latestAnswer;
        } else {
            System.out.println("Failed to fetch data: " + response.getStatusCode() + " " + response.getBody());
            return " I'm sorry, I couldn't find an answer";
        }
    }

    /**
     * Utilities not required by Interactive AI Holograms from here to end...
     */


    // `https://host:port/aiholo/tts?textToConvert=${encodeURIComponent(textToConvert)}
    // &languageCode=${encodeURIComponent(languageCode)}&ssmlGender=${encodeURIComponent(ssmlGender)}
    // &voiceName=${encodeURIComponent(voiceName)}`;
    @GetMapping("/tts")
    public ResponseEntity<byte[]> ttsAndReturnAudioFile(@RequestParam("textToConvert") String textToConvert,
                                                        @RequestParam("languageCode") String languageCode,
                                                        @RequestParam("ssmlGender") String ssmlGender,
                                                        @RequestParam("voiceName") String voiceName) throws Exception {
        System.out.println("TTS GCP  textToConvert = " + textToConvert + ", languageCode = " + languageCode +
                ", ssmlGender = " + ssmlGender + ", voiceName = " + voiceName);
        String resolvedVoiceName = resolveFemaleVoice(languageCode);
        if (!resolvedVoiceName.equals(voiceName)) {
            System.out.println("Ensuring female voice: overriding with " + resolvedVoiceName);
        }
        voiceName = resolvedVoiceName;
        try (TextToSpeechClient textToSpeechClient = TextToSpeechClient.create()) {
            System.out.println("in TTS GCP textToSpeechClient:" + textToSpeechClient + " languagecode:" + languageCode);
            SynthesisInput input = SynthesisInput.newBuilder().setText(textToConvert).build();
            VoiceSelectionParams voice =
                    VoiceSelectionParams.newBuilder()
                            .setLanguageCode(languageCode)
                            .setSsmlGender(SsmlVoiceGender.FEMALE) // Force female voice
                            .setName(voiceName) // e.g., "pt-BR-Wavenet-A"
                            .build();
            AudioConfig audioConfig =
                    AudioConfig.newBuilder()
                            .setAudioEncoding(AudioEncoding.LINEAR16) // wav AudioEncoding.MP3 being another
                            .build();
            SynthesizeSpeechResponse response =
                    textToSpeechClient.synthesizeSpeech(input, voice, audioConfig);
            ByteString audioContents = response.getAudioContent();
            byte[] audioData = audioContents.toByteArray();

            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.CONTENT_TYPE, "audio/mpeg");
            headers.set(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"tts-" + languageCode + "" + ssmlGender + "" + voiceName + "_" +
                            getFirst10Chars(textToConvert) + ".mp3\"");
            return new ResponseEntity<>(audioData, headers, HttpStatus.OK);
        }
    }



    @GetMapping("/langchain")
    @ResponseBody
    public String langchain(@RequestParam("question") String question,
                            @RequestParam("languageCode") String languageCode,
                            @RequestParam("voiceName") String voicename) throws Exception {
        EmbeddingSearchRequest embeddingSearchRequest = null;
        OracleEmbeddingStore embeddingStore =
                OracleEmbeddingStore.builder()
                        .dataSource(dataSource)
                        .embeddingTable(EmbeddingTable.builder()
                                .createOption(CreateOption.CREATE_OR_REPLACE)
                                .name("my_embedding_table")
                                .idColumn("id_column_name")
                                .embeddingColumn("embedding_column_name")
                                .textColumn("text_column_name")
                                .metadataColumn("metadata_column_name")
                                .build())
                        .index(Index.ivfIndexBuilder()
                                .createOption(CreateOption.CREATE_OR_REPLACE).build())
                        .build();
    embeddingStore.search(embeddingSearchRequest);
        return "langchain";
    }


    //set/get etc utilites to end....


    public static String getFirst10Chars(String textToConvert) {
        if (textToConvert == null || textToConvert.isEmpty()) {
            return "";
        }
        return textToConvert.length() > 10 ? textToConvert.substring(0, 10) : textToConvert;
    }


    @GetMapping("/set")
    @ResponseBody
    public String setValue(@RequestParam("value") String value) {
        theValue = value;
        System.out.println("EchoController set: " + theValue);
        String filePath = OUTPUT_FILE_PATH != null ? OUTPUT_FILE_PATH : "aiholo_output.txt";
        try (FileWriter writer = new FileWriter(filePath)) {
            JSONObject json = new JSONObject();
            json.put("data", value); // Store the response inside JSON
            writer.write(json.toString());
            writer.flush();
        } catch (IOException e) {
            return "Error writing to file: " + e.getMessage();
        }

        return "set successfully: " + theValue;

    }

    @GetMapping("/get")
    @ResponseBody
    public String getValue() {
        System.out.println("EchoController get: " + theValue);
        return theValue;
    }

    // Simple static string storage without file operations
    private static String simpleValue = "default";

    @GetMapping("/simple/set")
    @ResponseBody
    public String setSimpleValue(@RequestParam("value") String value) {
        simpleValue = value;
        System.out.println("Simple set: " + simpleValue);
        return "Simple value set successfully: " + simpleValue;
    }

    @GetMapping("/simple/get")
    @ResponseBody
    public String getSimpleValue() {
        System.out.println("Simple get: " + simpleValue);
        return simpleValue;
    }

    @GetMapping("/vision/analyze")
    @ResponseBody
    public String analyzeVisionImage(@RequestBody Map<String, String> payload) {
        System.out.println("Vision AI analysis requested");
        
        // Note: The VisionAIAgent now captures images automatically via webcam
        // when triggered with "what do you see" keywords.
        // This endpoint is kept for backwards compatibility but delegates to AgentService
        
        String question = payload.getOrDefault("question", "what do you see");
        
        // Use AgentService to process with VisionAIAgent
        AgentService.AgentResponse response = agentService.processQuestion(question, 30);
        
        if (response != null && response.getAnswer() != null) {
            return response.getAnswer();
        }
        
        return "Vision AI Agent is not available or failed to process request";
    }

    @GetMapping("/polling/stop")
    @ResponseBody
    public String stopPolling() {
        if (POLL_FOR_ANSWERS) {
            stopPollingClient();
            return "Polling client stopped";
        } else {
            return "Polling not enabled (POLL_FOR_ANSWERS=false)";
        }
    }

    @GetMapping("/polling/status")
    @ResponseBody
    public String getPollingStatus() {
        JSONObject status = new JSONObject();
        status.put("pollForAnswers", POLL_FOR_ANSWERS);
        status.put("redirectAnswer", REDIRECT_ANSWER);
        status.put("pollingActive", pollingClient != null && pollingClient.isActive());
        status.put("pollServerUrl", POLL_SERVER_URL);
        status.put("pollIntervalMs", POLL_INTERVAL_MS);
        status.put("lastProcessedTimestamp", pollingClient != null ? pollingClient.getLastProcessedTimestamp() : 0);
        return status.toString();
    }

    @GetMapping("/playarbitrary")
    @ResponseBody
    public String playArbitrary(
            @RequestParam("answer") String answer,
            @RequestParam("languageCode") String languageCode,
        @RequestParam("voiceName") String voicename,
        @RequestParam(value = "ttsMode", required = false) String ttsModeParam,
        @RequestParam(value = "audio2Face", required = false) Boolean audio2FaceParam,
        @RequestParam(value = "audioDelayMs", required = false, defaultValue = "500") Integer audioDelayMs) {
        System.out.println("playarbitrary answer = " + answer + ", languageCode = " + languageCode + ", voicename = " + voicename);
        String resolvedVoiceName = resolveFemaleVoice(languageCode);
        if (voicename == null || !voicename.equals(resolvedVoiceName)) {
            System.out.println("playArbitrary enforcing female voice: " + resolvedVoiceName);
        }
        voicename = resolvedVoiceName;
        try {
            TTSSelection ttsSelection = TTSSelection.fromParam(ttsModeParam);
            if (audio2FaceParam != null) {
                setAudio2FaceEnabled(audio2FaceParam);
            }
            // Note: audioDelayMs parameter is deprecated - use AUDIO_DELAY_MS in .env file instead
            boolean audio2FaceEnabled = isAudio2FaceEnabled();
            theValue = "question";
            String filePath = OUTPUT_FILE_PATH != null ? OUTPUT_FILE_PATH : "aiholo_output.txt";
            try (FileWriter writer = new FileWriter(filePath)) {
                JSONObject json = new JSONObject();
                json.put("data", theValue); // Store the response inside JSON
                writer.write(json.toString());
                writer.flush();
            } catch (IOException e) {
                return "Error writing to file: " + e.getMessage();
            }
            if (REDIRECT_ANSWER) {
                executeRedirect(answer, languageCode, voicename, "Manual playback", 0.0, ttsSelection, audio2FaceEnabled);
            } else {
                generateAndPlayTts("output.wav", answer, languageCode, voicename,
                    "Manual playback", 0.0, ttsSelection, audio2FaceEnabled);
            }
            return "OK";
        } catch (Exception e) {
            e.printStackTrace();
            return "Error: " + e.getMessage();
        }
    }
    

    /**
     * Execute redirect - stores answer parameters as JSON in GetSetController instead of playing audio
     */
    private void executeRedirect(String textToSay, String languageCode, String voiceName,
                                String aiPipelineLabel, double aiDurationMillis, TTSSelection ttsSelection,
                                boolean audio2FaceEnabled) {
        try {
            JSONObject json = new JSONObject();
            json.put("textToSay", textToSay != null ? textToSay : "");
            json.put("languageCode", languageCode != null ? languageCode : "");
            json.put("voiceName", voiceName != null ? voiceName : "");
            json.put("aiPipelineLabel", aiPipelineLabel != null ? aiPipelineLabel : "");
            json.put("aiDurationMillis", aiDurationMillis);
            json.put("ttsSelection", ttsSelection != null ? ttsSelection.name() : "");
            json.put("audio2FaceEnabled", audio2FaceEnabled);
            json.put("timestamp", System.currentTimeMillis());
            
            String jsonString = json.toString();
            GetSetController.setValue(jsonString);
            System.out.println("Redirect answer stored in GetSetController: " + jsonString);
        } catch (Exception e) {
            System.err.println("Error in executeRedirect: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void generateAndPlayTts(String fileName, String textToSay, String languageCode, String voiceName,
                                    String aiPipelineLabel, double aiDurationMillis, TTSSelection requestedMode,
                                    boolean audio2FaceEnabled) throws Exception {
        String safeText = (textToSay == null || textToSay.isBlank()) ? " " : textToSay;
        TTSSelection currentSelection = (requestedMode != null) ? requestedMode : TTSSelection.defaultSelection();
        Exception lastError = null;

        for (int attempt = 0; attempt < 2 && currentSelection != null; attempt++) {
            long ttsStartNs = System.nanoTime();
            try {
                executeTtsForSelection(fileName, safeText, languageCode, voiceName, currentSelection);
                double ttsDurationMillis = (System.nanoTime() - ttsStartNs) / 1_000_000.0;
                TTSCoquiEnhanced.registerPlaybackMetrics(
                        fileName,
                        new TTSCoquiEnhanced.PlaybackMetrics(
                                aiPipelineLabel,
                                aiDurationMillis,
                                ttsDurationMillis,
                                true,
                                currentSelection.getDisplayLabel()));
                // Use AudioOutputService for dual audio playback
                System.out.println("Playing audio via AudioOutputService: " + fileName);
                audioOutputService.playAudioToDualOutputs(fileName, AUDIO_DIR_PATH);
                return;
            } catch (Exception error) {
                double elapsedMillis = (System.nanoTime() - ttsStartNs) / 1_000_000.0;
                System.err.println("TTS mode " + currentSelection.name() + " failed after " +
                        String.format(Locale.ROOT, "%.2f", elapsedMillis) + " ms: " + error.getMessage());
                lastError = error;
                if (attempt == 0) {
                    currentSelection = TTSSelection.fallbackFor(currentSelection);
                    if (currentSelection != null) {
                        System.out.println("Attempting fallback TTS mode: " + currentSelection.name());
                        continue;
                    }
                }
                break;
            }
        }

        throw lastError != null ? lastError : new RuntimeException("Unable to generate TTS audio");
    }

    /**
     * Static wrapper for generateAndPlayTts to enable remote API polling functionality.
     * Creates a temporary controller instance to execute TTS generation.
     */
    public static void generateAndPlayTtsStatic(String fileName, String textToSay, String languageCode,
                                                String voiceName, String aiPipelineLabel, double aiDurationMillis,
                                                TTSSelection requestedMode, boolean audio2FaceEnabled) throws Exception {
        AIHoloController tempController = new AIHoloController();
        tempController.generateAndPlayTts(fileName, textToSay, languageCode, voiceName, aiPipelineLabel,
                                          aiDurationMillis, requestedMode, audio2FaceEnabled);
    }

    private void executeTtsForSelection(String fileName, String textToSay, String languageCode,
                                        String voiceName, TTSSelection selection) throws Exception {
        if (selection == null) {
            selection = TTSSelection.defaultSelection();
        }

        switch (selection.getEngine()) {
            case GCP:
                TTSAndAudio2Face.TTS(fileName, textToSay, languageCode, voiceName);
                break;
            case OCI:
                System.out.println("Oracle Cloud TTS not yet implemented; routing to Google Cloud TTS backend");
                TTSAndAudio2Face.TTS(fileName, textToSay, languageCode, voiceName);
                break;
            case COQUI:
                TTSCoquiEnhanced.TTSQuality quality = selection.getCoquiQuality() != null ?
                        selection.getCoquiQuality() : TTSCoquiEnhanced.TTSQuality.BALANCED;
                System.out.println("Generating Coqui TTS with quality " + quality);
                TTSCoquiEnhanced.generateCoquiTTS(fileName, textToSay, quality, languageCode, voiceName);
                break;
            default:
                throw new IllegalStateException("Unsupported TTS engine: " + selection.getEngine());
        }
    }

    /**
     * Configurable TTS processing method that routes to the appropriate TTS engine
     * based on the TTS_ENGINE environment variable
     */
    public static void processMetahuman(String fileName, String textToSay, String languageCode, String voiceName) {
        processMetahuman(fileName, textToSay, languageCode, voiceName, isAudio2FaceEnabled());
    }

    public static void processMetahuman(String fileName, String textToSay, String languageCode, String voiceName,
                                        boolean audio2FaceEnabled) {
        System.out.println("Processing TTS with engine: " + ACTIVE_TTS_ENGINE);
        System.out.println("Text: " + textToSay);
        String resolvedVoiceName = resolveFemaleVoice(languageCode);
        if (voiceName == null || !voiceName.equals(resolvedVoiceName)) {
            System.out.println("processMetahuman enforcing female voice: " + resolvedVoiceName);
        }
        voiceName = resolvedVoiceName;
        
        try {
            switch (ACTIVE_TTS_ENGINE) {
                case GCP:
                    System.out.println("Using Google Cloud TTS");
                    TTSAndAudio2Face.processMetahuman(fileName, textToSay, languageCode, voiceName, audio2FaceEnabled);
                    break;
                    
                case OCI:
                    System.out.println("Using Oracle Cloud Infrastructure TTS (placeholder)");
                    // TODO: Implement OCI TTS integration
                    // For now, fall back to GCP
                    System.out.println("OCI TTS not yet implemented, falling back to GCP");
                    TTSAndAudio2Face.processMetahuman(fileName, textToSay, languageCode, voiceName, audio2FaceEnabled);
                    break;
                    
                case COQUI:
                    System.out.println("Using Coqui TTS (high-quality offline neural TTS)");
                    processCoquiTTS(fileName, textToSay, languageCode, voiceName, audio2FaceEnabled);
                    break;
                    
                default:
                    System.err.println("Unknown TTS engine: " + ACTIVE_TTS_ENGINE + ", falling back to GCP");
                    TTSAndAudio2Face.processMetahuman(fileName, textToSay, languageCode, voiceName, audio2FaceEnabled);
                    break;
            }
        } catch (Exception e) {
            System.err.println("Error with " + ACTIVE_TTS_ENGINE + " TTS: " + e.getMessage());
            // Fallback to GCP if current engine fails
            if (ACTIVE_TTS_ENGINE != TTSEngine.GCP) {
                System.out.println("Falling back to Google Cloud TTS");
                try {
                    TTSAndAudio2Face.processMetahuman(fileName, textToSay, languageCode, voiceName, audio2FaceEnabled);
                } catch (Exception fallbackError) {
                    System.err.println("Fallback TTS also failed: " + fallbackError.getMessage());
                    playErrorAudio(audio2FaceEnabled);
                }
            } else {
                playErrorAudio(audio2FaceEnabled);
            }
        }
    }
    
    /**
     * Process TTS using Coqui TTS with fallback strategy
     */
    private static void processCoquiTTS(String fileName, String textToSay, String languageCode, String voiceName,
                                        boolean audio2FaceEnabled) throws Exception {
        try {
            // Determine TTS quality from centralized configuration
            String qualityEnv = Configuration.getTtsQuality();
            TTSCoquiEnhanced.TTSQuality quality = TTSCoquiEnhanced.TTSQuality.BALANCED;
            
            if (qualityEnv != null) {
                try {
                    quality = TTSCoquiEnhanced.TTSQuality.valueOf(qualityEnv.toUpperCase());
                } catch (IllegalArgumentException e) {
                    System.out.println("Invalid TTS_QUALITY: " + qualityEnv + ", using BALANCED");
                }
            }
            
            // Generate TTS using Coqui
            TTSCoquiEnhanced.generateCoquiTTS(fileName, textToSay, quality, languageCode, voiceName);
            
            // Handle Audio2Face or local playback
            if (audio2FaceEnabled) {
                TTSCoquiEnhanced.sendToAudio2Face(fileName);
            } else {
                TTSCoquiEnhanced.playAudioFile(fileName);
            }
            
        } catch (Exception e) {
            System.err.println("Coqui TTS failed: " + e.getMessage());
            throw e; // Re-throw to trigger fallback in processMetahuman
        }
    }
    
    /**
     * Play error audio when all TTS methods fail
     */
    private static void playErrorAudio(boolean audio2FaceEnabled) {
        try {
            if (audio2FaceEnabled) {
                TTSAndAudio2Face.sendToAudio2Face("tts-en-USFEMALEAoede_SorrySpeechToken.wav");
            } else {
                TTSAndAudio2Face.playAudioFile("tts-en-USFEMALEAoede_SorrySpeechToken.wav");
            }
        } catch (Exception errorAudioException) {
            System.err.println("Could not play error audio: " + errorAudioException.getMessage());
        }
    }
}

/**
 * en-US (American English):
 * •	en-US-Neural2-F
 * •	en-US-Neural2-G
 * •	en-US-Neural2-H
 * •	en-US-Neural2-I
 * •	en-US-Neural2-J
 * •	en-US-Standard-C
 * •	en-US-Standard-E
 * •	en-US-Standard-G
 * •	en-US-Standard-I
 * •	en-US-Wavenet-C
 * •	en-US-Wavenet-E
 * •	en-US-Wavenet-G
 * •	en-US-Wavenet-I
 * <p>
 * en-GB (British English):
 * •	en-GB-Neural2-C
 * •	en-GB-Neural2-E
 * •	en-GB-Standard-A
 * •	en-GB-Standard-C
 * •	en-GB-Standard-E
 * •	en-GB-Wavenet-A
 * •	en-GB-Wavenet-C
 * •	en-GB-Wavenet-E
 */
