package com.oracle.ai.ragagent.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request model for RAG queries
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Query request for the RAG system")
public class QueryRequest {
    
    @Schema(
        description = "The user's question to be answered using the knowledge base",
        example = "Tell me more about JSON Relational Duality",
        required = true
    )
    private String question;
}
