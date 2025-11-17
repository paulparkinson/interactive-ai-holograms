package oracleai.aiholo;

import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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

import javax.sql.*;

import java.sql.*;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.springframework.http.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import oracleai.aiholo.agents.*;
import java.util.*;

@Controller
@RequestMapping("/aiholo")
// @CrossOrigin(origins = "*")
public class AIHoloController {
    
    @Autowired
    private ChatGPTService chatGPTService;
    
    private String theValue = "mirrorme";
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private static final String SANDBOX_API_URL = System.getenv("SANDBOX_API_URL");
    private static final String AI_OPTIMZER = System.getenv("AI_OPTIMZER");
    private static final String OPENAI_KEY = System.getenv("OPENAI_KEY");
    // Langflow API configuration. These environment variables should be set
    // when deploying the application. LANGFLOW_SERVER_URL is the base URL for
    // the Langflow server (for example, http://host:7860/api), FLOW_ID is the
    // ID of the flow you wish to call, and LANGFLOW_API_KEY is your API key.
    private static final String LANGFLOW_SERVER_URL = System.getenv("LANGFLOW_SERVER_URL");
    private static final String LANGFLOW_FLOW_ID = System.getenv("LANGFLOW_FLOW_ID");
    private static final String LANGFLOW_API_KEY = System.getenv("LANGFLOW_API_KEY");
    static final String AUDIO_DIR_PATH = System.getenv("AUDIO_DIR_PATH");
    private static final String OUTPUT_FILE_PATH = System.getenv("OUTPUT_FILE_PATH");
    private static final String AIHOLO_HOST_URL = System.getenv("AIHOLO_HOST_URL");
    private static final AtomicBoolean AUDIO2FACE_ENABLED =
            new AtomicBoolean(Boolean.parseBoolean(System.getenv("IS_AUDIO2FACE")));
    private static final boolean REDIRECT_ANSWER = 
            Boolean.parseBoolean(System.getenv().getOrDefault("REDIRECT_ANSWER", "true"));
    private static int audioDelayMs = 500; // Default delay between dual audio outputs in milliseconds

    // Agent registration system
    private static final List<Agent> registeredAgents = new ArrayList<>();
    
    static {
        // Register agents at startup (order matters - first match wins)
        registerAgent(new MirrorMeAgent(OUTPUT_FILE_PATH));
        registerAgent(new DigitalTwinAgent(OUTPUT_FILE_PATH));
        registerAgent(new SignAgent(OUTPUT_FILE_PATH));
        registerAgent(new FinancialAgent(LANGFLOW_SERVER_URL, LANGFLOW_FLOW_ID, LANGFLOW_API_KEY));
        registerAgent(new GamerAgent(OPENAI_KEY));
        // OptimizerToolkitAgent commented out - backend not working
        // registerAgent(new OptimizerToolkitAgent(SANDBOX_API_URL, AI_OPTIMZER));
        // DirectLLMAgent is the default fallback (registered last, no keywords)
        registerAgent(new DirectLLMAgent(OPENAI_KEY));
    }

    /**
     * Register an agent to handle specific types of questions.
     * Agents are checked in registration order.
     */
    public static void registerAgent(Agent agent) {
        if (agent.isConfigured()) {
            registeredAgents.add(agent);
            System.out.println("Registered agent: " + agent.getName());
        } else {
            System.out.println("Skipping agent (not configured): " + agent.getName());
        }
    }

    /**
     * Find an agent that can handle the given question based on keyword matching.
     * @param question The normalized (lowercase) question
     * @return The matching agent, or null if no match
     */
    private static Agent findAgentForQuestion(String question) {
        Agent fallbackAgent = null;
        
        for (Agent agent : registeredAgents) {
            String[][] keywords = agent.getKeywords();
            
            // If agent has no keywords, it's a fallback agent (like DirectLLMAgent)
            if (keywords.length == 0) {
                fallbackAgent = agent;
                continue;
            }
            
            // Check if any keyword set matches
            for (String[] keywordSet : keywords) {
                boolean allMatch = true;
                for (String keyword : keywordSet) {
                    if (!question.contains(keyword.toLowerCase())) {
                        allMatch = false;
                        break;
                    }
                }
                if (allMatch) {
                    return agent;
                }
            }
        }
        
        // If no specific agent matched, return the fallback agent
        return fallbackAgent;
    }

    static boolean isAudio2FaceEnabled() {
        return AUDIO2FACE_ENABLED.get();
    }

    static void setAudio2FaceEnabled(boolean enabled) {
        AUDIO2FACE_ENABLED.set(enabled);
        System.out.println("Audio2Face playback " + (enabled ? "enabled" : "disabled"));
    }
    
    // TTS Engine Configuration
    private static final String TTS_ENGINE = System.getenv("TTS_ENGINE") != null ? 
        System.getenv("TTS_ENGINE").toUpperCase() : "GCP";
    
    // TTS Engine Options
    public enum TTSEngine {
        GCP,        // Google Cloud TTS (current default)
        OCI,        // Oracle Cloud Infrastructure TTS (placeholder)
        COQUI       // Coqui TTS (high-quality offline neural TTS)
    }

    enum TTSSelection {
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
                return COQUI_BALANCED;
            }
            switch (selection) {
                case COQUI_FAST:
                case COQUI_QUALITY:
                    return COQUI_BALANCED;
                case COQUI_BALANCED:
                    return GCP;
                case GCP:
                    return COQUI_BALANCED;
                case OCI:
                    return GCP;
                default:
                    return COQUI_BALANCED;
            }
        }

        public static TTSSelection defaultSelection() {
            return COQUI_BALANCED;
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
    
    private static int currentAnswerIntro = 0;
    private static String aiholo_prompt_additions = "";

    static {
        // Check for aiholo_prompt_additions.txt in AUDIO_DIR_PATH at startup
        if (AUDIO_DIR_PATH != null) {
            try {
                java.nio.file.Path additionsPath = Paths.get(AUDIO_DIR_PATH, "aiholo_prompt_additions.txt");
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

    static String resolveFemaleVoice(String languageCode) {
        String normalized = languageCode == null ? "" : languageCode.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return DEFAULT_FEMALE_VOICE;
        }
        return FEMALE_VOICE_MAP.getOrDefault(normalized, DEFAULT_FEMALE_VOICE);
    }

    public AIHoloController() {
//        startInactivityMonitor();
        // RemoteApiPoller.startPolling();
    }

    @SuppressWarnings("unused")
    private void startInactivityMonitor() {
        System.out.println("startInactivityMonitor...");
        scheduler.scheduleAtFixedRate(() -> {
            if (isRecentQuestionProcessed) {
                System.out.println("isRecentQuestionProcessed true so skipping the timecheck/keepalive");
                isRecentQuestionProcessed = false;
            }
//            String fileName = "currenttime.wav"; //testing123-brazil.wav
//            TTSAndAudio2Face.processMetahuman(
//                        fileName,  TimeInWords.getTimeInWords(languageCode),
//                    DEFAULT_LANGUAGE_CODE, DEFAULT_VOICE_NAME);
            if (isAudio2FaceEnabled()) {
                TTSCoquiEnhanced.sendToAudio2Face("explainer.wav");
            } else {
                // Play audio to VB-Audio Virtual Cable (for Unreal Live Link Hub)
                TTSCoquiEnhanced.playAudioFileToDevice("explainer.wav", "CABLE");
                // Wait ~800ms then play to default speakers
                try { Thread.sleep(audioDelayMs); } catch (InterruptedException e) { }
                TTSCoquiEnhanced.playAudioFile("explainer.wav");
            }
        }, 1, 15, TimeUnit.MINUTES);
    }


    @GetMapping("")
    public String home(@RequestParam(value = "languageCode", defaultValue = "en-US") String languageCode, Model model) {
        System.out.println("AIHolo root languageCode = " + languageCode);
        AIHoloController.languageCode = languageCode;
        model.addAttribute("languageCode", languageCode);
        model.addAttribute("aiholoHostUrl", AIHOLO_HOST_URL != null ? AIHOLO_HOST_URL : "http://localhost:8080");
        String resolvedVoice = resolveFemaleVoice(languageCode);
        model.addAttribute("voiceName", resolvedVoice);
        model.addAttribute("audio2FaceEnabled", isAudio2FaceEnabled());
        return "aiholo";
    }


    @GetMapping("/explainer")
    @ResponseBody
    public String explainer() throws Exception {
        System.out.println("AIHoloController.explainer");
        theValue = "explainer";
        String filePath = OUTPUT_FILE_PATH != null ? OUTPUT_FILE_PATH : "aiholo_output.txt";
        try (FileWriter writer = new FileWriter(filePath)) {
            JSONObject json = new JSONObject();
            json.put("data", theValue);
            writer.write(json.toString());
            writer.flush();
        } catch (IOException e) {
            return "Error writing to file: " + e.getMessage();
        }
        if (isAudio2FaceEnabled()) {
            TTSCoquiEnhanced.sendToAudio2Face("explainer.wav");
        } else {
            // Play audio for Unreal Live Link Hub source (first output)
            TTSCoquiEnhanced.playAudioFileToDevice("explainer.wav", "CABLE");
            // Wait ~800ms then play to local speaker (second output)
            try { Thread.sleep(audioDelayMs); } catch (InterruptedException e) { }
            TTSCoquiEnhanced.playAudioFile("explainer.wav");
        }

        return "Explained";
    }



    @GetMapping("/leia")
    @ResponseBody
    public String leia() throws Exception {
        System.out.println("AIHoloController.leia");
        theValue = "leia";
        String filePath = OUTPUT_FILE_PATH != null ? OUTPUT_FILE_PATH : "aiholo_output.txt";
        try (FileWriter writer = new FileWriter(filePath)) {
            JSONObject json = new JSONObject();
            json.put("data", theValue);
            writer.write(json.toString());
            writer.flush();
        } catch (IOException e) {
            return "Error writing to file: " + e.getMessage();
        }
        //     TTSAndAudio2Face.sendToAudio2Face("explainer-leia.wav");
        return "leia hologram";
    }


    @GetMapping("/play")
    @ResponseBody
    public String play(@RequestParam("question") String question,
                       @RequestParam("selectedMode") String selectedMode,
                       @RequestParam("languageCode") String languageCode,
                       @RequestParam("voiceName") String voicename,
                       @RequestParam(value = "ttsMode", required = false) String ttsModeParam,
                       @RequestParam(value = "audio2Face", required = false) Boolean audio2FaceParam,
                       @RequestParam(value = "audioDelayMs", required = false, defaultValue = "500") Integer audioDelayMs) throws Exception {
        System.out.println(
                "play question: " + question + " selectedMode: " + selectedMode +
                        " languageCode:" + languageCode + " voicename:" + voicename);
        System.out.println("modified question: " + question);
        String resolvedVoiceName = resolveFemaleVoice(languageCode);
        if (voicename == null || !voicename.equals(resolvedVoiceName)) {
            System.out.println("Overriding requested voice with female voice: " + resolvedVoiceName);
        }
        voicename = resolvedVoiceName;
        TTSSelection ttsSelection = TTSSelection.fromParam(ttsModeParam);
        System.out.println("Requested TTS mode: " + ttsSelection.name());
        if (audio2FaceParam != null) {
            setAudio2FaceEnabled(audio2FaceParam);
        }
        if (audioDelayMs != null) {
            AIHoloController.audioDelayMs = audioDelayMs;
            System.out.println("Audio delay set to: " + audioDelayMs + "ms");
        }
        final boolean audio2FaceEnabled = isAudio2FaceEnabled();
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
            String normalized = question.toLowerCase();
            // Commented out - this was interfering with GamerAgent keyword matching
            // boolean videoAgentIntent = normalized.contains("video") && normalized.contains("agent");
            // if (videoAgentIntent) {
            //     triggerVideoAgent("leia");
            //     return "Triggered video agent";
            // }   

        // Start a new thread to call TTSAndAudio2Face.sendToAudio2Face with intro switching
        new Thread(() -> {
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
        
        if (selectedMode.contains("use chatgpt")) {
            // Direct ChatGPT query mode
            question = question.replace("use chatgpt", "").trim();
            question += ". Respond in 25 words or less.";
            aiPipelineLabel = "ChatGPT Direct";
            long aiStartNs = System.nanoTime();
            answer = chatGPTService.queryChatGPT(question);
            aiDurationMillis = (System.nanoTime() - aiStartNs) / 1_000_000.0;
            
        } else if (selectedMode.contains("use vector")) {
            question = question.replace("use vectorrag", "").trim();
            question += ". Respond in 25 words or less. " + aiholo_prompt_additions;
            
            // Check if any registered agent can handle this question
            Agent matchingAgent = findAgentForQuestion(normalized);
            
            long aiStartNs = System.nanoTime();
            if (matchingAgent != null) {
                aiPipelineLabel = matchingAgent.getName();
                // Write agent value name to output file
                theValue = matchingAgent.getValueName();
                try (FileWriter writer = new FileWriter(filePath)) {
                    JSONObject json = new JSONObject();
                    json.put("data", theValue);
                    writer.write(json.toString());
                    writer.flush();
                } catch (IOException e) {
                    System.err.println("Error writing agent name to file: " + e.getMessage());
                }
                answer = matchingAgent.processQuestion(question);
            } else {
                aiPipelineLabel = "executeSandbox";
                answer = executeSandbox(question);
            }
            aiDurationMillis = (System.nanoTime() - aiStartNs) / 1_000_000.0;

        } else {
            if (selectedMode.contains("use narrate")) {
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
                generateAndPlayTts(fileName, answer, languageCode, voicename, aiPipelineLabel, aiDurationMillis, ttsSelection, audio2FaceEnabled);
            }
        } catch (Exception e) {
            System.err.println("Requested TTS mode failed completely: " + e.getMessage());
            // Fallback to original implementation
            processMetahuman(fileName, answer, languageCode, voicename, audio2FaceEnabled);
        }
        if (answer != null) {
            String lowercaseAnswer = answer.toLowerCase();
            if (lowercaseAnswer.contains("leia") || lowercaseAnswer.contains("star wars")) {
                Thread.sleep(5);
                leia();
            }
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
     * DEPRECATED: This method has been replaced by the FinancialAgent class.
     * The agent registration system now handles financial agent queries.
     * 
     * @deprecated Use the Agent registration system instead
     */
    /*
    public String executeFinancialAgent(String question) {
        System.out.println("using financial agent: " + question);
        if (LANGFLOW_SERVER_URL == null || LANGFLOW_FLOW_ID == null || LANGFLOW_API_KEY == null) {
            return "Error: Langflow configuration is not set";
        }
        // Build the URL. The /v1/run endpoint executes a flow by ID. The
        // stream=false query parameter disables token streaming.
        String url = LANGFLOW_SERVER_URL + "/v1/run/" + LANGFLOW_FLOW_ID + "?stream=false";
        // Construct the request payload
        Map<String, Object> payload = new HashMap<>();
        payload.put("output_type", "chat");
        payload.put("input_type", "chat");
        payload.put("input_value", question);

        JSONObject jsonPayload = new JSONObject(payload);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // Provide the API key for authentication
        headers.set("x-api-key", LANGFLOW_API_KEY);
        HttpEntity<String> request = new HttpEntity<>(jsonPayload.toString(), headers);
        RestTemplate restTemplate = new RestTemplate();
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);
            if (response.getStatusCode() == HttpStatus.OK) {
                // Ensure the response body is JSON before attempting to parse it.
                String body = response.getBody();
                if (body == null) {
                    return "Error: Empty response from Langflow";
                }
                String trimmedBody = body.trim();
                if (!trimmedBody.startsWith("{") && !trimmedBody.startsWith("[")) {
                    // Langflow returned a non‑JSON response (e.g. HTML error page).
                    // Return the raw body so the caller can log or display it.
                    return trimmedBody;
                }
                JSONObject responseData;
                try {
                    responseData = new JSONObject(trimmedBody);
                } catch (Exception e) {
                    // The body looked like JSON but failed to parse.
                    return "Error parsing Langflow response: " + e.getMessage();
                }
                // Parse the JSON response and extract the message. The message is
                // nested under outputs[0].outputs[0].outputs.message.message or
                // outputs[0].outputs[0].results.message.text depending on the
                // flow configuration. We'll attempt multiple paths.
                try {
                    // Older flows: outputs[0].outputs[0].outputs.message.message
                    String message = responseData
                            .getJSONArray("outputs")
                            .getJSONObject(0)
                            .getJSONArray("outputs")
                            .getJSONObject(0)
                            .getJSONObject("outputs")
                            .getJSONObject("message")
                            .getString("message");
                    if (message != null && !message.isEmpty()) {
                        return message;
                    }
                } catch (Exception ignore) {
                    // fall through to next attempt
                }
                try {
                    // Newer flows: outputs[0].outputs[0].results.message.text
                    String message = responseData
                            .getJSONArray("outputs")
                            .getJSONObject(0)
                            .getJSONArray("outputs")
                            .getJSONObject(0)
                            .getJSONObject("results")
                            .getJSONObject("message")
                            .getString("text");
                    if (message != null && !message.isEmpty()) {
                        return message;
                    }
                } catch (Exception ignore) {
                    // fall through to next attempt
                }
                try {
                    // Fallback: check nested data text (results.message.data.text)
                    String message = responseData
                            .getJSONArray("outputs")
                            .getJSONObject(0)
                            .getJSONArray("outputs")
                            .getJSONObject(0)
                            .getJSONObject("results")
                            .getJSONObject("message")
                            .getJSONObject("data")
                            .getString("text");
                    if (message != null && !message.isEmpty()) {
                        return message;
                    }
                } catch (Exception ignore) {
                    // no more attempts
                }
                return "Error parsing Langflow JSON response";
            } else {
                return "Error: " + response.getStatusCode() + " " + response.getBody();
            }
        } catch (Exception e) {
            return "Error calling Langflow: " + e.getMessage();
        }
    }
    */

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


    // Vector embedding, store, langchain, etc. stuff...


//    @Autowired
//    VectorStore vectorStore;
//
//    @GetMapping("/vectorstoretest")
//    @ResponseBody
//    public String vectorstoretest(@RequestParam("question") String question,
//                                  @RequestParam("selectedMode") String selectedMode,
//                                  @RequestParam("languageCode") String languageCode,
//                                  @RequestParam("voiceName") String voicename) throws Exception {
////        System.out.println(
//        List<Document> documents = List.of(
//                new Document("Spring AI rocks!! Spring AI rocks!!", Map.of("meta1", "meta1")),
//                new Document("The World is Big and Salvation Lurks Around the Corner"),
//                new Document("You walk forward facing the past and you turn back toward the future.",  Map.of("meta2", "meta2")));
//        // Add the documents to Oracle Vector Store
//        vectorStore.add(documents);
//        // Retrieve documents similar to a query
//        List<Document> results =
//                vectorStore.similaritySearch(SearchRequest.builder().query(question).topK(5).build());
////                vectorStore.similaritySearch(SearchRequest.builder().query("Spring").topK(5).build());
//        return "test";
//        //results.getFirst().getFormattedContent(); give s cannot find symbol
//        //[ERROR]   symbol:   method getFirst()
//        //[ERROR]   location: variable results of type java.util.List<org.springframework.ai.document.Document>
//    }

    @GetMapping("/langchain")
    @ResponseBody
    public String langchain(@RequestParam("question") String question,
                            @RequestParam("selectedMode") String selectedMode,
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
            if (audioDelayMs != null) {
                AIHoloController.audioDelayMs = audioDelayMs;
                System.out.println("Audio delay set to: " + audioDelayMs + "ms");
            }
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
        generateAndPlayTts("output.wav", answer, languageCode, voicename,
            "Manual playback", 0.0, ttsSelection, audio2FaceEnabled);
            return "OK";
        } catch (Exception e) {
            e.printStackTrace();
            return "Error: " + e.getMessage();
        }
    }
    
    // Commented out - video agent functionality moved to agent system
    /*
    private static void triggerVideoAgent(String providedValue) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setBasicAuth("oracleai", "oraclespatialai");
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            String baseUrl = "http://150.136.102.87:8080/status/aiholo/set";
            URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl)
                    .queryParam("type", "video")
                    .queryParam("value", providedValue)
                    .build(true)
                    .toUri();
            ResponseEntity<String> response = restTemplate.exchange(uri, HttpMethod.GET, entity, String.class);
            System.out.println("Video agent status=" + response.getStatusCode());
            if (response.getBody() != null && !response.getBody().isBlank()) {
                System.out.println("Video agent response body: " + response.getBody());
            }
        } catch (Exception e) {
            System.err.println("Failed to notify video agent: " + e.getMessage());
        }

        final boolean audio2FaceEnabled = isAudio2FaceEnabled();
        new Thread(() -> {
            try {
                if (audio2FaceEnabled) {
                    TTSAndAudio2Face.sendToAudio2Face("tts-en-USFEMALEAoede_playingvideo.wav");
                } else {
                    // Play audio for Unreal Live Link Hub source (first output)
                    TTSAndAudio2Face.playAudioFileToDevice("tts-en-USFEMALEAoede_playingvideo.wav", "CABLE");
                    // Wait ~800ms then play to local speaker (second output)
                    try { Thread.sleep(audioDelayMs); } catch (InterruptedException e) { }
                    TTSAndAudio2Face.playAudioFile("tts-en-USFEMALEAoede_playingvideo.wav");
                }
            } catch (Exception audioError) {
                System.err.println("Failed to play video agent audio: " + audioError.getMessage());
            }
        }).start();
    }
    */

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
                if (audio2FaceEnabled) {
                    TTSCoquiEnhanced.sendToAudio2Face(fileName);
                } else {
                    // Play audio for Unreal Live Link Hub source (first output)
                    TTSCoquiEnhanced.playAudioFileToDevice(fileName, "CABLE");
                    // Wait ~800ms then play to local speaker (second output)
                    try { Thread.sleep(audioDelayMs); } catch (InterruptedException e) { }
                    TTSCoquiEnhanced.playAudioFile(fileName);
                }
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
     * Static wrapper for generateAndPlayTts to allow external classes (like RemoteApiPoller) to trigger TTS.
     */
    static void generateAndPlayTtsStatic(String fileName, String textToSay, String languageCode, String voiceName,
                                         String aiPipelineLabel, double aiDurationMillis, TTSSelection requestedMode,
                                         boolean audio2FaceEnabled) throws Exception {
        // Create a temporary instance to call the instance method
        // Note: This works because generateAndPlayTts doesn't depend on instance state
        new AIHoloController().generateAndPlayTts(fileName, textToSay, languageCode, voiceName,
                aiPipelineLabel, aiDurationMillis, requestedMode, audio2FaceEnabled);
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
            // Determine TTS quality from environment or use BALANCED as default
            String qualityEnv = System.getenv("TTS_QUALITY");
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
