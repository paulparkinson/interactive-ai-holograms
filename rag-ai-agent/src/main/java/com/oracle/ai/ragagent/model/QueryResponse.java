package com.oracle.ai.ragagent.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response model for RAG queries
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response from the RAG system")
public class QueryResponse {
    
    @Schema(
        description = "The generated answer based on retrieved context",
        example = "JSON Relational Duality in Oracle Database 23ai allows you to work with data as both JSON documents and relational tables simultaneously..."
    )
    private String answer;
    
    @Schema(
        description = "Source of the information",
        example = "Oracle Database 23ai Vector RAG"
    )
    private String source;
    
    @Schema(
        description = "Time taken to generate the response in milliseconds",
        example = "1250"
    )
    private Long responseTimeMs;
}
