package oracleai.aiholo;

import java.nio.file.Paths;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

import com.google.cloud.texttospeech.v1.*;
import com.google.protobuf.ByteString;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TTSLocal {
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    private static final String PIPER_EXE_PATH = "C:\\Users\\holoai\\Downloads\\piper_windows_amd64\\piper\\piper.exe"; //REPLACE WITH YOUR OWN PATH

    public static void processMetahuman(String fileName, String textToSay, String languageCode, String voiceName) {
        executor.submit(() -> {
            try {
                TTS(fileName, textToSay, languageCode, voiceName);
                sendToAudio2Face(fileName);
            } catch (Exception e) {
                System.out.println("processMetahuman exception during TTS:" + e);
                // com.google.api.gax.rpc.UnavailableException: io.grpc.StatusRuntimeException:
                // UNAVAILABLE: Credentials failed to obtain metadata
                // will occur if token expired
                // TODO might be funny and helpful to do this, ie have the system gives its
                // status and ask for help ...
                // sendToAudio2Face("uhoh-lookslikeIneedanewTTStoken.wav");
                sendToAudio2Face("../audio-aiholo/explainer.wav");
                // sendToAudio2Face("hello-brazil.wav");
            }

        });
    }

    public static void googleTTS(String fileName, String text, String languageCode, String voicename) throws Exception {
        try (TextToSpeechClient textToSpeechClient = TextToSpeechClient.create()) {
            System.out.println("in TTS  languagecode:" + languageCode + " voicename:" + voicename + " text:" + text);
            SynthesisInput input = SynthesisInput.newBuilder().setText(text).build();
            // "最受欢迎的游戏是Pods Of Kon。").build();
            // "最も人気のあるビデオゲームは「Pods Of Kon」です。").build();
            VoiceSelectionParams voice = VoiceSelectionParams.newBuilder()
                    .setLanguageCode(languageCode) // ja-JP, en-US, ...
                    .setSsmlGender(SsmlVoiceGender.FEMALE) // NEUTRAL, MALE
                    .setName(voicename) // "Kore" pt-BR-Wavenet-D
                    .build();
            AudioConfig audioConfig = AudioConfig.newBuilder()
                    .setAudioEncoding(AudioEncoding.LINEAR16) // wav AudioEncoding.MP3
                    .build();
            SynthesizeSpeechResponse response = textToSpeechClient.synthesizeSpeech(input, voice, audioConfig);
            ByteString audioContents = response.getAudioContent();
            String fullPath = Paths.get(AIHoloController.AUDIO_DIR_PATH, fileName).toString();
            java.nio.file.Path audioPath = Paths.get(fullPath);

            try {
                java.nio.file.Files.deleteIfExists(audioPath); // delete old file
            } catch (Exception e) {
                System.err.println("Could not delete previous file: " + e.getMessage());
            }

            try (OutputStream out = new FileOutputStream(fullPath)) {
                out.write(audioContents.toByteArray());
                System.out.println("Audio content written to file:" + fullPath);
            }

        }
    }

    //SAMPLE CODE FOR PIPER TTS
    public static void TTS(String fileName, String text, String languageCode, String voicename) throws Exception {
        System.out.println("DEBUG: Received voicename parameter: '" + voicename + "'");

        // If voicename is just a filename, construct the full path
        String modelPath;
        if (voicename.contains("\\") || voicename.contains("/")) {
            // Already a full path
            modelPath = voicename;
        } else if (voicename.endsWith(".onnx")) {
            // Just a filename, construct full path
            modelPath = "C:\\Users\\holoai\\Downloads\\piper_windows_amd64\\piper\\" + voicename;
        } else {
            // Fallback to Google Cloud TTS style voice name - use default Piper model
            modelPath = "C:\\Users\\holoai\\Downloads\\piper_windows_amd64\\piper\\en_US-kathleen-low.onnx";
        }

        System.out.println("DEBUG: Using model path: " + modelPath);
        System.out.println("DEBUG: Model file exists: " + java.nio.file.Files.exists(Paths.get(modelPath)));
        System.out.println("Using Piper TTS - model:" + modelPath + " text:" + text);

        String fullPath = Paths.get(AIHoloController.AUDIO_DIR_PATH, fileName).toString();
        java.nio.file.Path audioPath = Paths.get(fullPath);

        // Delete existing file
        try {
            java.nio.file.Files.deleteIfExists(audioPath);
        } catch (Exception e) {
            System.err.println("Could not delete previous file: " + e.getMessage());
        }

        try {
            // Create Piper process - use the constructed model path
            ProcessBuilder piperBuilder = new ProcessBuilder(
                    PIPER_EXE_PATH, "-m", modelPath, "-f", fullPath);
            piperBuilder.redirectErrorStream(true);
            Process piperProcess = piperBuilder.start();

            // Write text to Piper's stdin
            try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(piperProcess.getOutputStream()))) {
                writer.println(text);
                writer.flush();
            }

            // Read process output for debugging
            BufferedReader reader = new BufferedReader(new InputStreamReader(piperProcess.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("Piper: " + line);
            }

            int exitCode = piperProcess.waitFor();
            if (exitCode == 0) {
                System.out.println("Piper TTS completed successfully. Audio saved to: " + fullPath);
            } else {
                throw new RuntimeException("Piper TTS failed with exit code: " + exitCode);
            }

        } catch (Exception e) {
            System.err.println("Error running Piper TTS: " + e.getMessage());
            throw e;
        }
    }

    public static void sendToAudio2Face(String fileName) {
        System.out.print("sendToAudio2Face for fileName:" + fileName + " ...");
        RestTemplate restTemplate = new RestTemplate();
        String baseUrl = "http://localhost:8011/A2F/Player/";

        String setRootPathUrl = baseUrl + "SetRootPath";
        Map<String, Object> rootPathPayload = new HashMap<>();
        rootPathPayload.put("a2f_player", "/World/audio2face/Player");
        rootPathPayload.put("dir_path", AIHoloController.AUDIO_DIR_PATH);
        sendPostRequest(restTemplate, setRootPathUrl, rootPathPayload);

        String setTrackUrl = baseUrl + "SetTrack";
        Map<String, Object> trackPayload = new HashMap<>();
        trackPayload.put("a2f_player", "/World/audio2face/Player");
        trackPayload.put("file_name", fileName);

        trackPayload.put("time_range", new int[] { 0, -1 });
        sendPostRequest(restTemplate, setTrackUrl, trackPayload);

        String playTrackUrl = baseUrl + "Play";
        Map<String, Object> playPayload = new HashMap<>();
        playPayload.put("a2f_player", "/World/audio2face/Player");
        sendPostRequest(restTemplate, playTrackUrl, playPayload);
        System.out.println(" ...sendToAudio2Face complete");
    }

    private static void sendPostRequest(RestTemplate restTemplate, String url, Map<String, Object> payload) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
        if (response.getStatusCode().is2xxSuccessful()) {
            // System.out.println("Successfully sent request to: " + url);
        } else {
            System.err.println("Failed to send request to " + url + ". Response: " + response.getBody());
        }
    }

}