package com.oracle.ai.ragagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Oracle RAG AI Agent - Spring Boot Application
 * 
 * Provides REST API for Retrieval-Augmented Generation (RAG) using:
 * - Oracle Database 23ai for vector storage
 * - Vertex AI embeddings (text-embedding-004)
 * - Gemini LLM (gemini-2.5-flash) for response generation
 * - LangChain4j for orchestration
 */
@SpringBootApplication
public class RagAiAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(RagAiAgentApplication.class, args);
    }
}
