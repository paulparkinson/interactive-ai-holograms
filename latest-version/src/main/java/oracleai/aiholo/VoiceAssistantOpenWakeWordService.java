package oracleai.aiholo;

import com.google.cloud.texttospeech.v1.*;
import com.google.cloud.speech.v1.*;
import com.google.cloud.language.v1.*;
import com.google.api.gax.rpc.ClientStream;
import com.google.api.gax.rpc.ResponseObserver;
import com.google.api.gax.rpc.StreamController;
import com.google.protobuf.ByteString;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sound.sampled.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Voice Assistant Service using OpenWakeWord for wake word detection
 * OpenWakeWord is an open-source Python library that runs locally
 */
@Service("openWakeWordVoiceAssistant")
public class VoiceAssistantOpenWakeWordService {
    
    @Autowired
    private AgentService agentService;
    
    @Autowired
    private AudioOutputService audioOutputService;
    
    @Autowired
    private AgentStateService agentStateService;
    
    // Conversation history for context (used by agents)
    private static List<Map<String, String>> conversationHistory = new ArrayList<>();
    
    // Configuration from centralized Configuration class
    private static final boolean ENABLE_LANGUAGE_DETECTION = Configuration.isLanguageDetectionEnabled();
    private static final String RESPONSE_LANGUAGE = Configuration.getResponseLanguage();
    
    private static String detectedLanguage = "en-US";
    private static String streamingDetectedLanguage = "en-US";
    private static boolean languageDetectedDuringStream = false;
    
    private Process pythonProcess;
    private BufferedReader pythonOutput;
    private BufferedWriter pythonInput;
    
    public void runDemo(
            String pythonScriptPath,
            String modelName,
            int audioDeviceIndex) {

        AudioFormat format = new AudioFormat(16000f, 16, 1, true, false);

        // get audio capture device
        DataLine.Info dataLineInfo = new DataLine.Info(TargetDataLine.class, format);
        TargetDataLine micDataLine;
        try {
            micDataLine = getAudioDevice(audioDeviceIndex, dataLineInfo);
            micDataLine.open(format);
        } catch (LineUnavailableException e) {
            System.err.println(
                    "Failed to get a valid capture device. Use --show_audio_devices to " +
                    "show available capture devices and their indices");
            return;
        }

        try {
            // Start Python OpenWakeWord subprocess
            startOpenWakeWordProcess(pythonScriptPath, modelName);
            
            micDataLine.start();
            System.out.println("Listening for wake word using OpenWakeWord: " + modelName);
            System.out.println("Press enter to stop recording...");

            // Buffer for processing audio (OpenWakeWord uses 1280 samples = 80ms at 16kHz)
            int frameLength = 1280;
            ByteBuffer captureBuffer = ByteBuffer.allocate(frameLength * 2);
            captureBuffer.order(ByteOrder.LITTLE_ENDIAN);
            short[] audioBuffer = new short[frameLength];

            int numBytesRead;
            while (System.in.available() == 0) {

                // read a buffer of audio
                numBytesRead = micDataLine.read(captureBuffer.array(), 0, captureBuffer.capacity());

                // don't pass to OpenWakeWord if we don't have a full buffer
                if (numBytesRead != frameLength * 2) {
                    continue;
                }

                // copy into 16-bit buffer
                captureBuffer.asShortBuffer().get(audioBuffer);

                // Send audio to Python OpenWakeWord process
                boolean detected = sendAudioToOpenWakeWord(audioBuffer);
                
                if (detected) {
                    System.out.printf("[%s] Wake word detected!\n",
                            LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
                    
                    // Trigger Google Cloud Streaming STT
                    System.out.println("Listening for speech...");
                    String transcription = performStreamingSTT(micDataLine, "en-US");
                    if (transcription != null && !transcription.isEmpty()) {
                        System.out.printf("[%s] You said: %s\n",
                                LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                                transcription);
                        
                        // Detect language from transcription (if enabled)
                        if (ENABLE_LANGUAGE_DETECTION) {
                            detectedLanguage = detectLanguage(transcription);
                        } else {
                            detectedLanguage = "en-US";
                        }
                        
                        // Check if command is to stop audio playback
                        String trimmedTranscription = transcription.trim().toLowerCase();
                        if (trimmedTranscription.equals("stop") || trimmedTranscription.equals("stop please") || 
                            trimmedTranscription.equals("please stop")) {
                            System.out.println("[" + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "] Stop command detected - stopping audio playback");
                            audioOutputService.stopAllAudio();
                            playTextToSpeech("Audio stopped");
                        }
                        // Process all other questions (including clear history) with AgentService
                        else {
                            System.out.println("[" + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "] Processing request with AgentService...");
                            AgentService.AgentResponse response = agentService.processQuestion(transcription, 30);
                            if (response != null && response.getAnswer() != null && !response.getAnswer().isEmpty()) {
                                System.out.printf("[%s] %s Response (%.2fms): %s\n",
                                        LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                                        response.getAgentName(),
                                        response.getDurationMs(),
                                        response.getAnswer());
                                
                                // Write agent state for hologram selection
                                agentStateService.writeAgentResponse(response);
                                
                                System.out.println("[" + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "] Playing response...");
                                playTextToSpeech(response.getAnswer());
                            } else {
                                System.out.println("[" + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "] No response from agent.");
                            }
                        }
                    } else {
                        System.out.println("[" + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "] No speech detected.");
                    }
                }
            }
        } catch (Exception e) {
            System.err.println(e.toString());
            e.printStackTrace();
        } finally {
            stopOpenWakeWordProcess();
        }
    }
    
    /**
     * Start the Python OpenWakeWord process
     */
    private void startOpenWakeWordProcess(String pythonScriptPath, String modelName) throws IOException {
        System.out.println("Starting OpenWakeWord Python process...");
        
        ProcessBuilder pb = new ProcessBuilder("python", pythonScriptPath, modelName);
        pb.redirectErrorStream(true);
        pythonProcess = pb.start();
        
        pythonOutput = new BufferedReader(new InputStreamReader(pythonProcess.getInputStream()));
        pythonInput = new BufferedWriter(new OutputStreamWriter(pythonProcess.getOutputStream()));
        
        // Wait for initialization message
        String line = pythonOutput.readLine();
        if (line != null && line.contains("READY")) {
            System.out.println("OpenWakeWord initialized successfully");
        } else {
            throw new IOException("Failed to initialize OpenWakeWord: " + line);
        }
    }
    
    /**
     * Send audio frame to Python OpenWakeWord process and check for detection
     */
    private boolean sendAudioToOpenWakeWord(short[] audioData) {
        try {
            // Convert audio to comma-separated string
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < audioData.length; i++) {
                if (i > 0) sb.append(",");
                sb.append(audioData[i]);
            }
            sb.append("\n");
            
            pythonInput.write(sb.toString());
            pythonInput.flush();
            
            // Read response
            String response = pythonOutput.readLine();
            return response != null && response.trim().equals("DETECTED");
            
        } catch (IOException e) {
            System.err.println("Error communicating with OpenWakeWord process: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Stop the Python OpenWakeWord process
     */
    private void stopOpenWakeWordProcess() {
        if (pythonProcess != null) {
            try {
                pythonInput.write("QUIT\n");
                pythonInput.flush();
                pythonProcess.waitFor(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                System.err.println("Error stopping OpenWakeWord process: " + e.getMessage());
            } finally {
                pythonProcess.destroy();
            }
        }
    }

    private void showAudioDevices() {
        Mixer.Info[] allMixerInfo = AudioSystem.getMixerInfo();
        Line.Info captureLine = new Line.Info(TargetDataLine.class);

        for (int i = 0; i < allMixerInfo.length; i++) {
            Mixer mixer = AudioSystem.getMixer(allMixerInfo[i]);
            if (mixer.isLineSupported(captureLine)) {
                System.out.printf("Device %d: %s\n", i, allMixerInfo[i].getName());
            }
        }
    }

    private TargetDataLine getDefaultCaptureDevice(DataLine.Info dataLineInfo)
            throws LineUnavailableException {

        if (!AudioSystem.isLineSupported(dataLineInfo)) {
            throw new LineUnavailableException(
                            "Default capture device does not support the format required " +
                            "by OpenWakeWord (16kHz, 16-bit, linearly-encoded, single-channel PCM).");
        }

        return (TargetDataLine) AudioSystem.getLine(dataLineInfo);
    }

    private String performStreamingSTT(TargetDataLine micDataLine, String languageCode) {
        // Reset language detection flags for new streaming session
        languageDetectedDuringStream = false;
        streamingDetectedLanguage = "en-US";
        StringBuilder transcriptBuilder = new StringBuilder();
        BlockingQueue<String> transcriptQueue = new LinkedBlockingQueue<>();
        final boolean[] stopCapture = {false};
        
        try (SpeechClient speechClient = SpeechClient.create()) {
            System.out.println("[" + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "] Starting streaming recognition...");
            
            RecognitionConfig.Builder configBuilder = RecognitionConfig.newBuilder()
                    .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
                    .setSampleRateHertz(16000)
                    .setLanguageCode(languageCode);
            
            configBuilder.addAlternativeLanguageCodes("zh-CN");
            configBuilder.addAlternativeLanguageCodes("es-ES");
            configBuilder.addAlternativeLanguageCodes("fr-FR");
            configBuilder.addAlternativeLanguageCodes("de-DE");
            configBuilder.addAlternativeLanguageCodes("ja-JP");
            configBuilder.addAlternativeLanguageCodes("ko-KR");
            
            RecognitionConfig config = configBuilder.build();
            
            StreamingRecognitionConfig streamingConfig = StreamingRecognitionConfig.newBuilder()
                    .setConfig(config)
                    .setInterimResults(true)
                    .build();
            
            ResponseObserver<StreamingRecognizeResponse> responseObserver = new ResponseObserver<StreamingRecognizeResponse>() {
                @Override
                public void onStart(StreamController controller) {
                }
                
                @Override
                public void onResponse(StreamingRecognizeResponse response) {
                    for (StreamingRecognitionResult result : response.getResultsList()) {
                        if (result.getAlternativesCount() > 0) {
                            String transcript = result.getAlternatives(0).getTranscript();
                            
                            if (result.getIsFinal()) {
                                // Final result - try to detect language from this transcript if not already done
                                if (!languageDetectedDuringStream && !transcript.trim().isEmpty()) {
                                    try {
                                        String detectedLang = detectLanguage(transcript);
                                        if (!detectedLang.equals("en-US")) {
                                            streamingDetectedLanguage = detectedLang;
                                        }
                                        languageDetectedDuringStream = true;
                                    } catch (Exception e) {
                                        // Silent fail - keep using default language
                                        languageDetectedDuringStream = true;
                                    }
                                }
                                
                                // Check if "please" or equivalents in detected language (for early termination)
                                if (isPleaseTrigger(transcript, streamingDetectedLanguage)) {
                                    stopCapture[0] = true;
                                }
                                
                                // Print with timestamp
                                System.out.printf("[%s] %s\n",
                                        LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                                        transcript);
                                transcriptQueue.offer(transcript);
                            } else {
                                // Interim result - print without newline for live feedback
                                System.out.print("\r" + transcript);
                                System.out.flush();
                            }
                        }
                    }
                }
                
                @Override
                public void onComplete() {
                    transcriptQueue.offer("__COMPLETE__");
                }
                
                @Override
                public void onError(Throwable t) {
                    System.err.println("Error during streaming recognition: " + t.getMessage());
                    transcriptQueue.offer("__ERROR__");
                }
            };
            
            ClientStream<StreamingRecognizeRequest> clientStream = speechClient.streamingRecognizeCallable().splitCall(responseObserver);
            
            StreamingRecognizeRequest configRequest = StreamingRecognizeRequest.newBuilder()
                    .setStreamingConfig(streamingConfig)
                    .build();
            clientStream.send(configRequest);
            
            byte[] buffer = new byte[6400];
            long startTime = System.currentTimeMillis();
            long maxDuration = 40000;
            
            try {
                while (System.currentTimeMillis() - startTime < maxDuration && 
                       System.in.available() == 0 && 
                       !stopCapture[0]) {
                    int bytesRead = micDataLine.read(buffer, 0, buffer.length);
                    if (bytesRead > 0) {
                        StreamingRecognizeRequest audioRequest = StreamingRecognizeRequest.newBuilder()
                                .setAudioContent(ByteString.copyFrom(buffer, 0, bytesRead))
                                .build();
                        clientStream.send(audioRequest);
                    }
                }
                
                if (stopCapture[0]) {
                    System.out.println("\n[" + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "] 'Please' detected, ending capture...");
                }
            } catch (IOException e) {
                System.err.println("Error reading from microphone: " + e.getMessage());
            }
            
            clientStream.closeSend();
            System.out.println("\n[" + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "] Audio streaming complete.");
            
            String transcript;
            while ((transcript = transcriptQueue.poll(2, TimeUnit.SECONDS)) != null) {
                if (transcript.equals("__COMPLETE__") || transcript.equals("__ERROR__")) {
                    break;
                }
                if (!transcript.trim().isEmpty()) {
                    transcriptBuilder.append(transcript).append(" ");
                }
            }
            
        } catch (Exception e) {
            System.err.println("Error performing streaming STT: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
        
        return transcriptBuilder.toString().trim();
    }

    private String detectLanguage(String text) {
        try (LanguageServiceClient language = LanguageServiceClient.create()) {
            Document doc = Document.newBuilder()
                    .setContent(text)
                    .setType(Document.Type.PLAIN_TEXT)
                    .build();
            
            AnalyzeSentimentResponse response = language.analyzeSentiment(doc);
            String detectedLanguage = response.getLanguage();
            
            String languageCode = mapLanguageCode(detectedLanguage);
            System.out.println("[" + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "] Detected language: " + languageCode);
            return languageCode;
        } catch (Exception e) {
            System.err.println("Error detecting language: " + e.getMessage());
            return "en-US";
        }
    }

    private String mapLanguageCode(String code) {
        Map<String, String> codeMap = new HashMap<>();
        codeMap.put("en", "en-US");
        codeMap.put("es", "es-ES");
        codeMap.put("fr", "fr-FR");
        codeMap.put("de", "de-DE");
        codeMap.put("it", "it-IT");
        codeMap.put("pt", "pt-BR");
        codeMap.put("ru", "ru-RU");
        codeMap.put("zh", "zh-CN");
        codeMap.put("ja", "ja-JP");
        codeMap.put("ko", "ko-KR");
        codeMap.put("ar", "ar-SA");
        codeMap.put("hi", "hi-IN");
        
        return codeMap.getOrDefault(code, "en-US");
    }

    private boolean isPleaseTrigger(String transcript) {
        return isPleaseTrigger(transcript, "en-US");
    }

    private boolean isPleaseTrigger(String transcript, String detectedLang) {
        String lower = transcript.toLowerCase();
        
        if (detectedLang.startsWith("en")) {
            return lower.contains("please");
        } else if (detectedLang.startsWith("zh")) {
            return transcript.contains("请") || transcript.contains("麻烦") || transcript.contains("拜托");
        } else if (detectedLang.startsWith("es")) {
            return lower.contains("por favor");
        } else if (detectedLang.startsWith("fr")) {
            return lower.contains("plaît") || lower.contains("s'il");
        } else if (detectedLang.startsWith("de")) {
            return lower.contains("bitte");
        } else if (detectedLang.startsWith("it")) {
            return lower.contains("favore") || lower.contains("piacere");
        } else if (detectedLang.startsWith("pt")) {
            return lower.contains("favor") || lower.contains("gentileza");
        } else if (detectedLang.startsWith("ru")) {
            return transcript.contains("пожалуйста");
        } else if (detectedLang.startsWith("ja")) {
            return transcript.contains("ください") || transcript.contains("お願い");
        } else if (detectedLang.startsWith("ko")) {
            return transcript.contains("주세요") || transcript.contains("부탁");
        } else if (detectedLang.startsWith("ar")) {
            return transcript.contains("من فضلك") || transcript.contains("لو سمحت");
        } else if (detectedLang.startsWith("hi")) {
            return transcript.contains("कृपया") || transcript.contains("प्रिय");
        }
        
        return false;
    }

    private void playTextToSpeech(String text) {
        try {
            try (TextToSpeechClient textToSpeechClient = TextToSpeechClient.create()) {
                SynthesisInput input = SynthesisInput.newBuilder()
                        .setText(text)
                        .build();

                String voiceLanguageCode = "en-US";
                String voiceName = "en-US-Neural2-C";
                
                if ("same".equals(RESPONSE_LANGUAGE) && !detectedLanguage.equals("en-US")) {
                    voiceLanguageCode = detectedLanguage;
                    voiceName = getVoiceForLanguage(detectedLanguage);
                }

                VoiceSelectionParams voice = VoiceSelectionParams.newBuilder()
                        .setLanguageCode(voiceLanguageCode)
                        .setName(voiceName)
                        .build();

                AudioConfig audioConfig = AudioConfig.newBuilder()
                        .setAudioEncoding(com.google.cloud.texttospeech.v1.AudioEncoding.LINEAR16)
                        .setSampleRateHertz(16000)
                        .build();

                SynthesizeSpeechResponse response = textToSpeechClient.synthesizeSpeech(input, voice, audioConfig);
                com.google.protobuf.ByteString audioContent = response.getAudioContent();

                byte[] audioBytes = audioContent.toByteArray();
                playAudio(audioBytes);
            }
        } catch (Exception e) {
            System.err.println("Error during text-to-speech: " + e.getMessage());
        }
    }

    private String getVoiceForLanguage(String languageCode) {
        Map<String, String> voiceMap = new HashMap<>();
        voiceMap.put("en-US", "en-US-Neural2-C");
        voiceMap.put("zh-CN", "cmn-CN-Wavenet-A");
        voiceMap.put("es-ES", "es-ES-Neural2-A");
        voiceMap.put("fr-FR", "fr-FR-Neural2-A");
        voiceMap.put("de-DE", "de-DE-Neural2-A");
        voiceMap.put("ja-JP", "ja-JP-Neural2-B");
        voiceMap.put("ko-KR", "ko-KR-Neural2-A");
        voiceMap.put("it-IT", "it-IT-Neural2-A");
        voiceMap.put("pt-BR", "pt-BR-Neural2-A");
        voiceMap.put("ru-RU", "ru-RU-Wavenet-A");
        voiceMap.put("ar-SA", "ar-XA-Wavenet-A");
        voiceMap.put("hi-IN", "hi-IN-Neural2-A");
        
        return voiceMap.getOrDefault(languageCode, "en-US-Neural2-C");
    }

    private void playAudio(byte[] audioData) {
        try {
            AudioFormat format = new AudioFormat(16000f, 16, 1, true, false);
            audioOutputService.playRawAudioToDualOutputs(audioData, format);
        } catch (Exception e) {
            System.err.println("Error playing audio: " + e.getMessage());
        }
    }

    private TargetDataLine getAudioDevice(int deviceIndex, DataLine.Info dataLineInfo)
            throws LineUnavailableException {

        if (deviceIndex >= 0) {
            try {
                Mixer.Info mixerInfo = AudioSystem.getMixerInfo()[deviceIndex];
                Mixer mixer = AudioSystem.getMixer(mixerInfo);

                if (mixer.isLineSupported(dataLineInfo)) {
                    return (TargetDataLine) mixer.getLine(dataLineInfo);
                }
            } catch (Exception e) {
                System.err.printf(
                        "No capture device found at index %s. Using default capture device.",
                        deviceIndex);
            }
        }

        return getDefaultCaptureDevice(dataLineInfo);
    }
}
