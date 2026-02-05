package oracleai.aiholo.agents;

import oracleai.vectorrag.service.VectorRAGService;
import org.springframework.stereotype.Component;

/**
 * Oracle Document Agent that performs vector search and RAG queries
 * using the VectorRAGService integration with Spring AI and Oracle 23ai.
 * 
 * This agent leverages the same vector RAG functionality available through
 * the /vectorrag/rag endpoint to provide contextual answers from uploaded documents.
 */
@Component
public class OracleDocAgent implements Agent {
    
    private final VectorRAGService vectorRAGService;
    
    public OracleDocAgent(VectorRAGService vectorRAGService) {
        this.vectorRAGService = vectorRAGService;
    }
    
    @Override
    public String getName() {
        return "Oracle Document Agent";
    }

    @Override
    public String getValueName() {
        return "oracledoc";
    }

    @Override
    public String[][] getKeywords() {
        // Triggers on document-related queries
        return new String[][] {
            {"search", "documents"},
            {"search", "documentation"},
            {"find", "documents"},
            {"rechercher" , "documents"}
        };
    }

    @Override
    public boolean isConfigured() {
        return vectorRAGService != null && vectorRAGService.getVectorStore() != null;
    }

    @Override
    public String processQuestion(String question) {
        System.out.println("Oracle Document Agent processing: " + question);
        
        if (!isConfigured()) {
            return "I'm sorry, the document search feature is not available right now. Please check the configuration.";
        }
        
        try {
            // Use VectorRAGService to perform RAG query
            String answer = vectorRAGService.rag(question);
            System.out.println("Oracle Document Agent successfully processed query via VectorRAGService");
            return answer;
            
        } catch (Exception e) {
            System.err.println("Error in Oracle Document Agent: " + e.getMessage());
            e.printStackTrace();
            return "I'm having difficulty searching the documents right now. Let me help you with something else instead.";
        }
    }
}
