package com.example.demo.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Controller
public class UploadController {

    @Value("${VECTOR_WORKER_URL:http://localhost:8001}")
    private String vectorWorkerUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> upload(@RequestParam("file") MultipartFile file) throws IOException {
        String url = vectorWorkerUrl + "/embed";
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            });

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, requestEntity, String.class);
            return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Upload failed: " + e.getMessage());
        }
    }

    @PostMapping("/search")
    @ResponseBody
    public ResponseEntity<String> search(
            @RequestParam("q") String query,
            @RequestParam(value = "top_k", defaultValue = "10") int topK) {
        String url = vectorWorkerUrl + "/search";
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("q", query);
            body.add("top_k", String.valueOf(topK));

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, requestEntity, String.class);
            return ResponseEntity.status(response.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(response.getBody());
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }

    @PostMapping("/rag")
    @ResponseBody
    public ResponseEntity<String> rag(
            @RequestParam("q") String query,
            @RequestParam(value = "top_k", defaultValue = "5") int topK) {
        String url = vectorWorkerUrl + "/rag";
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("q", query);
            body.add("top_k", String.valueOf(topK));

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, requestEntity, String.class);
            return ResponseEntity.status(response.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(response.getBody());
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"error\": \"Search failed: " + e.getMessage() + "\"}");
        }
    }

    @GetMapping("/list")
    @ResponseBody
    public ResponseEntity<String> list() {
        String url = vectorWorkerUrl + "/list";
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            return ResponseEntity.status(response.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(response.getBody());
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"error\": \"List failed: " + e.getMessage() + "\"}");
        }
    }


    @DeleteMapping("/delete/{docId}")
    @ResponseBody
    public ResponseEntity<String> deleteDocument(@PathVariable String docId) {
        String url = vectorWorkerUrl + "/documents/" + docId;
        try {
            restTemplate.delete(url);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"success\": true, \"id\": \"" + docId + "\"}");
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"success\": false, \"error\": \"" + e.getMessage() + "\"}");
        }
    }

    @GetMapping("/config")
    @ResponseBody
    public ResponseEntity<String> getConfig() {
        String url = vectorWorkerUrl + "/config";
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            return ResponseEntity.status(response.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(response.getBody());
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"error\": \"Failed to get config: " + e.getMessage() + "\"}");
        }
    }

    @PostMapping("/config")
    @ResponseBody
    public ResponseEntity<String> updateConfig(@org.springframework.web.bind.annotation.RequestBody String body) {
        String url = vectorWorkerUrl + "/config";
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> requestEntity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, requestEntity, String.class);
            return ResponseEntity.status(response.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(response.getBody());
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"error\": \"Failed to update config: " + e.getMessage() + "\"}");
        }
    }

    @GetMapping("/databases")
    @ResponseBody
    public ResponseEntity<String> listDatabases() {
        String url = vectorWorkerUrl + "/databases";
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            return ResponseEntity.status(response.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(response.getBody());
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"error\": \"Failed to list databases: " + e.getMessage() + "\"}");
        }
    }

    @PostMapping("/databases/switch")
    @ResponseBody
    public ResponseEntity<String> switchDatabase(@org.springframework.web.bind.annotation.RequestBody String body) {
        String url = vectorWorkerUrl + "/databases/switch";
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> requestEntity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, requestEntity, String.class);
            return ResponseEntity.status(response.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(response.getBody());
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"error\": \"Failed to switch database: " + e.getMessage() + "\"}");
        }
    }

    @PostMapping("/setup/import-onnx-model")
    @ResponseBody
    public ResponseEntity<String> importOnnxModel(@org.springframework.web.bind.annotation.RequestBody String body) {
        String url = vectorWorkerUrl + "/setup/import-onnx-model";
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> requestEntity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, requestEntity, String.class);
            return ResponseEntity.status(response.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(response.getBody());
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"error\": \"Failed to import ONNX model: " + e.getMessage() + "\"}");
        }
    }

    @GetMapping("/setup/check-onnx-model")
    @ResponseBody
    public ResponseEntity<String> checkOnnxModel() {
        String url = vectorWorkerUrl + "/setup/check-onnx-model";
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            return ResponseEntity.status(response.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(response.getBody());
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"error\": \"Failed to check ONNX model: " + e.getMessage() + "\"}");
        }
    }
}

