// Copyright (c) 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl/

package oracleai.vectorrag.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import oracleai.vectorrag.service.VectorRAGService;
import oracleai.vectorrag.model.MessageDTO;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.CrossOrigin;

@RestController
@RequestMapping("vectorrag")
@CrossOrigin(originPatterns = "*", allowedHeaders = "*")
public class VectorRAGController {

    private final EmbeddingClient embeddingClient;
    private final ChatClient chatClient;
    private final VectorRAGService vectorRAGService;

    @Value("${vectorrag.temp-dir:tempDir}")
    private String TEMP_DIR;

    private static final Logger logger = LoggerFactory.getLogger(VectorRAGController.class);

    @Autowired
    public VectorRAGController(EmbeddingClient embeddingClient, ChatClient chatClient, VectorRAGService vectorRAGService) {
        this.embeddingClient = embeddingClient;
        this.chatClient = chatClient;
        this.vectorRAGService = vectorRAGService;
        logger.info("VectorRAGController started.");
    }

    @GetMapping("/ping")
    public String ping(@RequestParam(value = "message", defaultValue = "Vector RAG is active") String message) {
        return "Vector RAG: " + message;
    }

    @PostMapping("/generate")
    public Map<String, Object> generate(@RequestBody MessageDTO message) {
        return Map.of("generation", chatClient.call(message.getMessage()));
    }

    @PostMapping("/rag")
    public Map<String, Object> rag(@RequestBody MessageDTO message) {
        return Map.of("generation", this.vectorRAGService.rag(message.getMessage()));
    }

    @PostMapping("/embedding")
    public Map<String, Object> embed(@RequestBody MessageDTO message) {
        EmbeddingResponse embeddingResponse = this.embeddingClient.embedForResponse(List.of(message.getMessage()));
        return Map.of("embedding", embeddingResponse);
    }

    @PostMapping("/store")
    public String uploadFile(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return "Failed to upload empty file.";
        }
        try {
            String currentDir = System.getProperty("user.dir");
            Path parentDir = Path.of(currentDir);
            Path tempPath = parentDir.resolve(TEMP_DIR);
            if (!Files.exists(tempPath)) {
                Files.createDirectories(tempPath);
            }
            Path tempDir = Files.createTempDirectory(tempPath, "vectorrag_uploads_");
            Path filePath = tempDir.resolve(file.getOriginalFilename());
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            this.vectorRAGService.putDocument(new FileSystemResource(filePath.toString()));

            return "File stored successfully " + filePath;
        } catch (IOException e) {
            logger.info(e.getMessage());
            return "Failed to upload file: " + e.getMessage();
        }
    }

    @GetMapping("/delete")
    public Map<String, Object> delete(@RequestParam(value = "id", defaultValue = "XXXXXXXXX") List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of("return", "No IDs provided");
        }
        Optional<Boolean> ret = vectorRAGService.getVectorStore().delete(ids);
        return Map.of("return", ret.get().toString());
    }

    @PostMapping("/search-similar")
    public List<Map<String, Object>> search(@RequestBody MessageDTO message) {
        List<Map<String, Object>> resultList = new ArrayList<>();
        List<Document> similarDocs = this.vectorRAGService.getSimilarDocs(message.getMessage());

        for (Document d : similarDocs) {
            Map<String, Object> metadata = d.getMetadata();
            Map<String, Object> doc = new HashMap<>();
            doc.put("id", d.getId());
            doc.put("text", d.getContent());
            doc.put("metadata", metadata);
            resultList.add(doc);
        }
        return resultList;
    }
}
