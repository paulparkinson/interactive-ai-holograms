package com.oracle.ai.ragagent.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Error response model
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Error response")
public class ErrorResponse {
    
    @Schema(description = "Error message", example = "Failed to connect to Oracle Database")
    private String error;
    
    @Schema(description = "HTTP status code", example = "500")
    private Integer status;
    
    @Schema(description = "Timestamp of the error", example = "2026-01-09T10:30:00Z")
    private String timestamp;
}
