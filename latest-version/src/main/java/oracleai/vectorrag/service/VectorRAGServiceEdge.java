// Copyright (c) 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl/

package oracleai.vectorrag.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * VectorRAGServiceEdge - Edge-based RAG service using Oracle Database 23ai
 * 
 * This service leverages the generate_text_response_all_docs SQL function which:
 * - Performs vector similarity search across all documents in vector_store
 * - Uses TINYBERT_MODEL for embeddings
 * - Calls Ollama LLM (llama3.2:3b) for response generation
 * - Returns response with associated document IDs
 */
@Service
public class VectorRAGServiceEdge {

    private static final Logger logger = LoggerFactory.getLogger(VectorRAGServiceEdge.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Perform RAG query using Oracle Database function
     * 
     * @param question The user's question
     * @return AI-generated response based on vector search results
     */
    public String rag(String question) {
        return rag(question, 3);
    }

    /**
     * Perform RAG query with custom topN parameter
     * 
     * @param question The user's question
     * @param topN Number of most relevant chunks to retrieve (default: 3)
     * @return AI-generated response based on vector search results
     */
    public String rag(String question, int topN) {
        try {
            logger.info("EdgeRAG query: {} (topN: {})", question, topN);
            
            String sql = "SELECT generate_text_response_all_docs(?, ?) AS response FROM dual";
            String jsonResponse = jdbcTemplate.queryForObject(sql, String.class, question, topN);
            
            logger.info("EdgeRAG response received: {} chars", jsonResponse != null ? jsonResponse.length() : 0);
            
            // Parse the JSON response to extract the actual answer
            if (jsonResponse != null && jsonResponse.contains("\"response\"")) {
                // Extract the response field from JSON
                int startIdx = jsonResponse.indexOf("\"response\":") + 11;
                int endIdx = jsonResponse.lastIndexOf(",\"doc_ids\"");
                if (endIdx == -1) {
                    endIdx = jsonResponse.lastIndexOf("}");
                }
                
                if (startIdx > 11 && endIdx > startIdx) {
                    String response = jsonResponse.substring(startIdx, endIdx).trim();
                    // Remove quotes if present
                    if (response.startsWith("\"") && response.endsWith("\"")) {
                        response = response.substring(1, response.length() - 1);
                    }
                    // Handle nested JSON response from Ollama
                    if (response.contains("\"response\":")) {
                        int nestedStart = response.indexOf("\"response\":\"") + 12;
                        int nestedEnd = response.lastIndexOf("\"");
                        if (nestedStart > 12 && nestedEnd > nestedStart) {
                            response = response.substring(nestedStart, nestedEnd);
                        }
                    }
                    return response;
                }
            }
            
            // If parsing fails, return the raw response
            return jsonResponse != null ? jsonResponse : "No response received from the database.";
            
        } catch (Exception e) {
            logger.error("Error in EdgeRAG query: {}", e.getMessage(), e);
            return "I'm sorry, but I encountered an error processing your question: " + e.getMessage();
        }
    }

    /**
     * Check if the service is properly configured
     * 
     * @return true if JdbcTemplate is available
     */
    public boolean isConfigured() {
        return jdbcTemplate != null;
    }
}
