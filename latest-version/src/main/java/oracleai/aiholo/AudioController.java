package oracleai.aiholo;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/audio")
@CrossOrigin(origins = "*") // so the static HTML can call it from file:// or another port
public class AudioController {
    private final AudioPlayerService service;

    public AudioController(AudioPlayerService service) {
        this.service = service;
    }

    @GetMapping("/mixers")
    public ResponseEntity<?> mixers() {
        return ResponseEntity.ok(Map.of("mixers", service.listOutputMixers()));
    }

    @PostMapping("/play")
    public ResponseEntity<?> play(@RequestBody Map<String, String> body) throws Exception {
        String path = body.get("path");
        String mixer = body.get("mixer"); // optional: pass part of the mixer name to target VoiceMeeter/Virtual Cable/etc.
        if (path == null || path.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing 'path' to .wav file"));
        }
        service.play(path, Optional.ofNullable(mixer).filter(s -> !s.isBlank()));
        return ResponseEntity.ok(Map.of("status", "playing", "path", path, "mixer", mixer));
    }

    @PostMapping("/play-upload")
    public ResponseEntity<?> playUpload(@RequestParam("file") MultipartFile file, 
                                       @RequestParam(value = "mixer", required = false) String mixer) throws Exception {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No file uploaded"));
        }
        
        // Validate file type
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".wav")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Only .wav files are supported"));
        }
        
        try {
            // Create temporary directory if it doesn't exist
            Path tempDir = Paths.get(System.getProperty("java.io.tmpdir"), "audio-uploads");
            Files.createDirectories(tempDir);
            
            // Save uploaded file to temporary location
            Path tempFile = tempDir.resolve("uploaded_" + System.currentTimeMillis() + "_" + filename);
            Files.copy(file.getInputStream(), tempFile);
            
            // Play the file
            service.play(tempFile.toString(), Optional.ofNullable(mixer).filter(s -> !s.isBlank()));
            
            return ResponseEntity.ok(Map.of(
                "status", "playing", 
                "filename", filename, 
                "mixer", mixer != null ? mixer : "",
                "tempPath", tempFile.toString()
            ));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to save uploaded file: " + e.getMessage()));
        }
    }

    @PostMapping("/play-simple")
    public ResponseEntity<?> playSimple() throws Exception {
        String hardcodedPath = "C:\\src\\github.com\\paulparkinson\\interactive-ai-holograms\\latest-version\\src\\main\\resources\\static\\audio-aiholo\\explainer.wav";
        
        try {
            service.play(hardcodedPath, Optional.empty());
            return ResponseEntity.ok(Map.of("status", "playing", "path", hardcodedPath));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to play audio: " + e.getMessage()));
        }
    }

    @GetMapping("/play-simple")
    public ResponseEntity<?> playSimpleGet() throws Exception {
        String hardcodedPath = "C:\\src\\github.com\\paulparkinson\\interactive-ai-holograms\\latest-version\\src\\main\\resources\\static\\audio-aiholo\\explainer.wav";
        
        try {
            service.play(hardcodedPath, Optional.empty());
            return ResponseEntity.ok(Map.of("status", "playing", "path", hardcodedPath));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to play audio: " + e.getMessage()));
        }
    }

    @PostMapping("/stop")
    public ResponseEntity<?> stop() {
        service.stop();
        return ResponseEntity.ok(Map.of("status", "stopped"));
    }

    @GetMapping("/stop")
    public ResponseEntity<?> stopGet() {
        service.stop();
        return ResponseEntity.ok(Map.of("status", "stopped"));
    }

    @GetMapping("/status")
    public ResponseEntity<?> status() {
        return ResponseEntity.ok(Map.of("playing", service.isPlaying()));
    }
}

