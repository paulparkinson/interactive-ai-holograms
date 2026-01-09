package com.oracle.ai.ragagent.controller;

import com.oracle.ai.ragagent.model.ErrorResponse;
import com.oracle.ai.ragagent.model.QueryRequest;
import com.oracle.ai.ragagent.model.QueryResponse;
import com.oracle.ai.ragagent.service.RagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

/**
 * REST Controller for RAG queries
 * Provides OpenAPI-documented endpoints for Dialogflow integration
 */
@Slf4j
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Oracle RAG API", description = "Retrieval-Augmented Generation API using Oracle Database 23ai and Vertex AI")
public class RagController {

    private final RagService ragService;

    public RagController(RagService ragService) {
        this.ragService = ragService;
    }

    @Operation(
        summary = "Query the RAG system",
        description = "Submit a question to retrieve relevant context from Oracle Database 23ai vector store and generate an answer using Vertex AI Gemini"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Successfully generated response",
            content = @Content(schema = @Schema(implementation = QueryResponse.class))
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid request - question is required",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Internal server error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        )
    })
    @PostMapping("/query")
    public ResponseEntity<?> query(@RequestBody QueryRequest request) {
        long startTime = System.currentTimeMillis();
        
        log.info("Received query request: {}", request.getQuestion());
        
        // Validate request
        if (request.getQuestion() == null || request.getQuestion().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(
                ErrorResponse.builder()
                    .error("Question is required and cannot be empty")
                    .status(HttpStatus.BAD_REQUEST.value())
                    .timestamp(Instant.now().toString())
                    .build()
            );
        }

        try {
            // Execute RAG query
            String answer = ragService.executeRagQuery(request.getQuestion());
            
            long duration = System.currentTimeMillis() - startTime;
            
            QueryResponse response = QueryResponse.builder()
                .answer(answer)
                .source("Oracle Database 23ai Vector RAG")
                .responseTimeMs(duration)
                .build();
            
            log.info("Successfully processed query in {} ms", duration);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error processing query", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                ErrorResponse.builder()
                    .error("Failed to process query: " + e.getMessage())
                    .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                    .timestamp(Instant.now().toString())
                    .build()
            );
        }
    }

    @Operation(
        summary = "Health check",
        description = "Check if the RAG service is healthy and database connection is working"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Service is healthy"),
        @ApiResponse(responseCode = "503", description = "Service is unavailable")
    })
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        boolean isHealthy = ragService.isHealthy();
        
        Map<String, String> response = new java.util.HashMap<>();
        response.put("status", isHealthy ? "UP" : "DOWN");
        response.put("timestamp", Instant.now().toString());
        
        return ResponseEntity
            .status(isHealthy ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
            .body(response);
    }
}
