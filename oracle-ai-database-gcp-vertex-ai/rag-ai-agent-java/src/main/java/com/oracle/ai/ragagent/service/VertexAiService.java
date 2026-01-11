package com.oracle.ai.ragagent.service;

import com.google.cloud.aiplatform.v1.*;
import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.api.GenerateContentResponse;
import com.google.cloud.vertexai.generativeai.ChatSession;
import com.google.cloud.vertexai.generativeai.ContentMaker;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import com.google.cloud.vertexai.generativeai.ResponseHandler;
import com.google.protobuf.Value;
import com.google.protobuf.util.JsonFormat;
import com.oracle.ai.ragagent.config.VertexAiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for interacting with Google Vertex AI
 * Handles embeddings and LLM generation
 */
@Slf4j
@Service
public class VertexAiService {

    private final VertexAiProperties props;
    private VertexAI vertexAI;
    private GenerativeModel model;
    private PredictionServiceClient predictionClient;
    private EndpointName embeddingEndpoint;

    public VertexAiService(VertexAiProperties props) {
        this.props = props;
    }

    @PostConstruct
    public void init() throws IOException {
        log.info("Initializing Vertex AI with project: {}, location: {}", 
                props.getProjectId(), props.getLocation());
        
        // Initialize Vertex AI SDK
        vertexAI = new VertexAI(props.getProjectId(), props.getLocation());
        
        // Initialize Generative Model for Gemini
        model = new GenerativeModel(props.getLlmModel(), vertexAI);
        
        // Initialize prediction client for embeddings
        predictionClient = PredictionServiceClient.create();
        
        // Create endpoint for embedding model
        embeddingEndpoint = EndpointName.ofProjectLocationPublisherModelName(
            props.getProjectId(),
            props.getLocation(),
            "google",
            props.getEmbeddingModel()
        );
        
        log.info("Vertex AI initialized with Gemini model: {}, Embedding model: {}", 
                props.getLlmModel(), props.getEmbeddingModel());
    }

    /**
     * Generate embedding vector for text using Vertex AI text-embedding-004
     */
    public List<Float> generateEmbedding(String text) throws IOException {
        log.debug("Generating embedding for text of length: {}", text.length());
        
        // Create instance for prediction
        Value.Builder instanceBuilder = Value.newBuilder();
        instanceBuilder.getStructValueBuilder()
            .putFields("content", Value.newBuilder().setStringValue(text).build());
        
        List<Value> instances = List.of(instanceBuilder.build());
        
        // Make prediction request
        PredictRequest predictRequest = PredictRequest.newBuilder()
            .setEndpoint(embeddingEndpoint.toString())
            .addAllInstances(instances)
            .build();
        
        PredictResponse response = predictionClient.predict(predictRequest);
        
        // Extract embedding from response
        List<Float> embedding = new ArrayList<>();
        if (response.getPredictionsCount() > 0) {
            Value prediction = response.getPredictions(0);
            Value embeddingsValue = prediction.getStructValue()
                .getFieldsOrThrow("embeddings");
            
            Value valuesValue = embeddingsValue.getStructValue()
                .getFieldsOrThrow("values");
            
            for (Value v : valuesValue.getListValue().getValuesList()) {
                embedding.add((float) v.getNumberValue());
            }
        }
        
        log.debug("Generated embedding with {} dimensions", embedding.size());
        return embedding;
    }

    /**
     * Generate response using Gemini LLM
     */
    public String generateResponse(String prompt) throws IOException {
        log.debug("Generating response for prompt of length: {}", prompt.length());
        
        // Create content from prompt
        var content = ContentMaker.fromString(prompt);
        
        // Generate response
        GenerateContentResponse response = model.generateContent(content);
        
        // Extract text from response
        String responseText = ResponseHandler.getText(response);
        
        log.debug("Generated response of length: {}", responseText.length());
        return responseText;
    }

    /**
     * Close resources
     */
    public void shutdown() {
        if (predictionClient != null) {
            predictionClient.close();
        }
        if (vertexAI != null) {
            vertexAI.close();
        }
    }
}
