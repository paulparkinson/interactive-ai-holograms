package oracleai.aiholo;

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
import java.io.File;
import javax.sound.sampled.*;
import java.nio.file.Paths;

public class TTSAndAudio2Face {
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    public static void processMetahuman(String fileName, String textToSay, String languageCode, String voiceName) {
        processMetahuman(fileName, textToSay, languageCode, voiceName, AIHoloController.isAudio2FaceEnabled());
    }

    public static void processMetahuman(String fileName, String textToSay, String languageCode, String voiceName,
                                        boolean audio2FaceEnabled) {
        executor.submit(() -> {
            try {
                TTS(fileName, textToSay, languageCode, voiceName);
                if (audio2FaceEnabled) {
                    sendToAudio2Face(fileName);
                } else {
                    playAudioFile(fileName);
                }
            } catch (Exception e) {
                System.out.println("processMetahuman exception during TTS:" + e);
                //com.google.api.gax.rpc.UnavailableException: io.grpc.StatusRuntimeException:
                // UNAVAILABLE: Credentials failed to obtain metadata
                // will occur if token expired
                //TODO might be funny and helpful to do this, ie have the system gives its status and ask for help ...
                // sendToAudio2Face("uhoh-lookslikeIneedanewTTStoken.wav");
                if (audio2FaceEnabled) {
                    sendToAudio2Face(AIHoloController.AUDIO_DIR_PATH + "tts-en-USFEMALEAoede_SorrySpeechToken.wav");
                } else {
                    playAudioFile("tts-en-USFEMALEAoede_SorrySpeechToken.wav");
                }
//                sendToAudio2Face("hello-brazil.wav");
            }

        });
    }


    public static void TTS(String fileName, String text, String languageCode, String voicename) throws Exception {
        try (TextToSpeechClient textToSpeechClient = TextToSpeechClient.create()) {
            System.out.println("in TTS  languagecode:" + languageCode + " voicename:" + voicename + " text:" + text);
            SynthesisInput input = SynthesisInput.newBuilder().setText(text).build();
                    //              "最受欢迎的游戏是Pods Of Kon。").build();
                    //  "最も人気のあるビデオゲームは「Pods Of Kon」です。").build();
            VoiceSelectionParams voice =
                    VoiceSelectionParams.newBuilder()
                            .setLanguageCode(languageCode) //ja-JP, en-US, ...
                            .setSsmlGender(SsmlVoiceGender.FEMALE) // NEUTRAL, MALE
                            .setName(voicename)  // "Kore" pt-BR-Wavenet-D
                            .build();
            AudioConfig audioConfig =
                    AudioConfig.newBuilder()
                            .setAudioEncoding(AudioEncoding.LINEAR16) // wav AudioEncoding.MP3
                            .build();
            SynthesizeSpeechResponse response =
                    textToSpeechClient.synthesizeSpeech(input, voice, audioConfig);
            ByteString audioContents = response.getAudioContent();
            String fullPath = AIHoloController.AUDIO_DIR_PATH + fileName;
            try (OutputStream out = new FileOutputStream(fullPath)) {
                out.write(audioContents.toByteArray());
            }
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
//            System.out.println("Successfully sent request to: " + url);
        } else {
            System.err.println("Failed to send request to " + url + ". Response: " + response.getBody());
        }
    }

    public static void playAudioFile(String filename) {
        // Play audio asynchronously to avoid blocking the calling thread
        new Thread(() -> {
            try {
                if (AIHoloController.AUDIO_DIR_PATH == null) {
                    System.err.println("AUDIO_DIR_PATH environment variable is not set");
                    return;
                }
                
                // Use Paths.get() for proper cross-platform path handling
                java.nio.file.Path audioPath = Paths.get(AIHoloController.AUDIO_DIR_PATH, filename);
                String fullPath = audioPath.toString();
                File audioFile = audioPath.toFile();
                
                System.out.println("Attempting to play: " + fullPath);
                System.out.println("File exists: " + audioFile.exists());
                System.out.println("File size: " + audioFile.length() + " bytes");
                
                if (!audioFile.exists()) {
                    System.err.println("Audio file not found: " + fullPath);
                    return;
                }
                
                System.out.println("Playing audio file on local machine: " + fullPath);
                
                // Use AudioSystem to play the WAV file on the local machine
                AudioInputStream audioStream = AudioSystem.getAudioInputStream(audioFile);
                AudioFormat format = audioStream.getFormat();
                System.out.println("Audio format: " + format);
                
                Clip clip = AudioSystem.getClip();
                clip.open(audioStream);
                
                // Set volume to maximum (if supported)
                if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                    FloatControl gainControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                    gainControl.setValue(gainControl.getMaximum());
                    System.out.println("Set volume to maximum: " + gainControl.getValue());
                }
                
                TTSCoquiEnhanced.PlaybackMetrics metrics = TTSCoquiEnhanced.consumePlaybackMetrics(filename);
                clip.start();
                if (metrics != null) {
                    System.out.println(metrics.formatForLog());
                }
                System.out.println("Audio playback started, duration: " + (clip.getMicrosecondLength() / 1000000.0) + " seconds");
                
                // Wait for the audio to finish playing
                Thread.sleep(clip.getMicrosecondLength() / 1000);
                
                clip.close();
                audioStream.close();
                
                System.out.println("Finished playing audio file: " + filename);
                
            } catch (Exception e) {
                System.err.println("Error playing audio file: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
    }





}

