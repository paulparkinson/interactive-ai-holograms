package oracleai.aiholo;

import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

@Controller
@RequestMapping("/aiholo")
// @CrossOrigin(origins = "*")
public class AIHoloController {
    private String theValue = "mirrorme";
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private static final String SANDBOX_API_URL = System.getenv("SANDBOX_API_URL");
    private static final String AI_OPTIMZER = System.getenv("AI_OPTIMZER");
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
    private static final boolean IS_AUDIO2FACE = Boolean.parseBoolean(System.getenv("IS_AUDIO2FACE"));
    
    // TTS Engine Configuration
    private static final String TTS_ENGINE = System.getenv("TTS_ENGINE") != null ? 
        System.getenv("TTS_ENGINE").toUpperCase() : "GCP";
    
    // TTS Engine Options
    public enum TTSEngine {
        GCP,        // Google Cloud TTS (current default)
        OCI,        // Oracle Cloud Infrastructure TTS (placeholder)
        COQUI       // Coqui TTS (high-quality offline neural TTS)
    }

    private enum TTSSelection {
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

    @Autowired
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

    private static String resolveFemaleVoice(String languageCode) {
        String normalized = languageCode == null ? "" : languageCode.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return DEFAULT_FEMALE_VOICE;
        }
        return FEMALE_VOICE_MAP.getOrDefault(normalized, DEFAULT_FEMALE_VOICE);
    }

    public AIHoloController() {
//        startInactivityMonitor();
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
            if (IS_AUDIO2FACE) {
                TTSCoquiEnhanced.sendToAudio2Face("explainer.wav");
            } else {
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
        if (IS_AUDIO2FACE) {
            TTSCoquiEnhanced.sendToAudio2Face("explainer.wav");
        } else {
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
                       @RequestParam(value = "ttsMode", required = false) String ttsModeParam) throws Exception {
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
            boolean videoAgentIntent = normalized.contains("video") && normalized.contains("agent");
            if (videoAgentIntent) {
                triggerVideoAgent("leia");
                return "Triggered video agent";
            }   

        // Start a new thread to call TTSAndAudio2Face.sendToAudio2Face with intro switching
        new Thread(() -> {
            try {
                // languagecode:es-MX voicename:es-US-Wavenet-A
                if (languageCode.equals("es-MX")) {
                    if (IS_AUDIO2FACE) {
                        TTSAndAudio2Face.sendToAudio2Face("tts-es-USFEMALEes-US-Wavenet-A_¡Claro!¡U.wav");
                    } else {
                        TTSAndAudio2Face.playAudioFile("tts-es-USFEMALEes-US-Wavenet-A_¡Claro!¡U.wav");
                    }
                } else {
                    // Switch for currentAnswerIntro
                    switch (currentAnswerIntro) {
                        case 0:
                            if (IS_AUDIO2FACE) {
                                TTSAndAudio2Face.sendToAudio2Face("tts-en-USFEMALEAoede_Sure!Illcheck.wav");
                            } else {
                                TTSAndAudio2Face.playAudioFile("tts-en-USFEMALEAoede_Sure!Illcheck.wav");
                            }
                            break;
                        case 1:
                            if (IS_AUDIO2FACE) {
                                TTSAndAudio2Face.sendToAudio2Face("tts-en-USFEMALEAoede_on_it.wav");
                            } else {
                                TTSAndAudio2Face.playAudioFile("tts-en-USFEMALEAoede_on_it.wav");
                            }
                            break;
                        case 2:
                            if (IS_AUDIO2FACE) {
                                TTSAndAudio2Face.sendToAudio2Face("tts-en-USFEMALEAoede_one_sec.wav");
                            } else {
                                TTSAndAudio2Face.playAudioFile("tts-en-USFEMALEAoede_one_sec.wav");
                            }
                            break;
                        case 3:
                            if (IS_AUDIO2FACE) {
                                TTSAndAudio2Face.sendToAudio2Face("tts-en-USFEMALEAoede_hmm.wav");
                            } else {
                                TTSAndAudio2Face.playAudioFile("tts-en-USFEMALEAoede_hmm.wav");
                            }
                            break;
                        default:
                            if (IS_AUDIO2FACE) {
                                TTSAndAudio2Face.sendToAudio2Face("tts-en-USFEMALEAoede_Sure!Illcheck.wav");
                            } else {
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
        if (selectedMode.contains("use vector")) {
            question = question.replace("use vectorrag", "").trim();
            question += ". Respond in 25 words or less. " + aiholo_prompt_additions;
            // If the user asks about a financial agent, call the Langflow financial agent
            // instead of the generic sandbox. The comparison is case-insensitive to
            // capture variations like "Financial Agent" or "financial agent".
// Check if the string contains "financ" and "agent"
            boolean financialAgentIntent = normalized.contains("financ") && normalized.contains("agent");
            long aiStartNs = System.nanoTime();
            if (financialAgentIntent) {
                aiPipelineLabel = "executeFinancialAgent";
                answer = executeFinancialAgent(question);
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
            generateAndPlayTts(fileName, answer, languageCode, voicename, aiPipelineLabel, aiDurationMillis, ttsSelection);
        } catch (Exception e) {
            System.err.println("Requested TTS mode failed completely: " + e.getMessage());
            // Fallback to original implementation
            processMetahuman(fileName, answer, languageCode, voicename);
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
     * Invoke a Langflow flow that acts as a financial agent. This method builds
     * a JSON payload containing the user's question and sends it to the
     * Langflow /v1/run endpoint for the configured flow. It uses the
     * LANGFLOW_API_KEY for authentication and parses the nested response to
     * extract the chat message. The environment variables LANGFLOW_SERVER_URL
     * and LANGFLOW_FLOW_ID must be defined; otherwise, this method will
     * return an error message.
     *
     * @param question The user's question to send to the financial agent.
     * @return The agent's textual response or an error message if the call fails.
     */
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

    /**
     * Utilites not required by Interactive AI Holograms from here to end...
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
        @RequestParam(value = "ttsMode", required = false) String ttsModeParam) {
        System.out.println("playarbitrary answer = " + answer + ", languageCode = " + languageCode + ", voicename = " + voicename);
        String resolvedVoiceName = resolveFemaleVoice(languageCode);
        if (voicename == null || !voicename.equals(resolvedVoiceName)) {
            System.out.println("playArbitrary enforcing female voice: " + resolvedVoiceName);
        }
        voicename = resolvedVoiceName;
        try {
            TTSSelection ttsSelection = TTSSelection.fromParam(ttsModeParam);
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
                    "Manual playback", 0.0, ttsSelection);
            return "OK";
        } catch (Exception e) {
            e.printStackTrace();
            return "Error: " + e.getMessage();
        }
    }
    
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

        new Thread(() -> {
            try {
                if (IS_AUDIO2FACE) {
                    TTSAndAudio2Face.sendToAudio2Face("tts-en-USFEMALEAoede_playingvideo.wav");
                } else {
                    TTSAndAudio2Face.playAudioFile("tts-en-USFEMALEAoede_playingvideo.wav");
                }
            } catch (Exception audioError) {
                System.err.println("Failed to play video agent audio: " + audioError.getMessage());
            }
        }).start();
    }

    private void generateAndPlayTts(String fileName, String textToSay, String languageCode, String voiceName,
                                    String aiPipelineLabel, double aiDurationMillis, TTSSelection requestedMode) throws Exception {
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
                if (IS_AUDIO2FACE) {
                    TTSCoquiEnhanced.sendToAudio2Face(fileName);
                }
                TTSCoquiEnhanced.playAudioFile(fileName);
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
                    TTSAndAudio2Face.processMetahuman(fileName, textToSay, languageCode, voiceName);
                    break;
                    
                case OCI:
                    System.out.println("Using Oracle Cloud Infrastructure TTS (placeholder)");
                    // TODO: Implement OCI TTS integration
                    // For now, fall back to GCP
                    System.out.println("OCI TTS not yet implemented, falling back to GCP");
                    TTSAndAudio2Face.processMetahuman(fileName, textToSay, languageCode, voiceName);
                    break;
                    
                case COQUI:
                    System.out.println("Using Coqui TTS (high-quality offline neural TTS)");
                    processCoquiTTS(fileName, textToSay, languageCode, voiceName);
                    break;
                    
                default:
                    System.err.println("Unknown TTS engine: " + ACTIVE_TTS_ENGINE + ", falling back to GCP");
                    TTSAndAudio2Face.processMetahuman(fileName, textToSay, languageCode, voiceName);
                    break;
            }
        } catch (Exception e) {
            System.err.println("Error with " + ACTIVE_TTS_ENGINE + " TTS: " + e.getMessage());
            // Fallback to GCP if current engine fails
            if (ACTIVE_TTS_ENGINE != TTSEngine.GCP) {
                System.out.println("Falling back to Google Cloud TTS");
                try {
                    TTSAndAudio2Face.processMetahuman(fileName, textToSay, languageCode, voiceName);
                } catch (Exception fallbackError) {
                    System.err.println("Fallback TTS also failed: " + fallbackError.getMessage());
                    playErrorAudio();
                }
            } else {
                playErrorAudio();
            }
        }
    }
    
    /**
     * Process TTS using Coqui TTS with fallback strategy
     */
    private static void processCoquiTTS(String fileName, String textToSay, String languageCode, String voiceName) throws Exception {
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
            if (IS_AUDIO2FACE) {
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
    private static void playErrorAudio() {
        try {
            if (IS_AUDIO2FACE) {
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