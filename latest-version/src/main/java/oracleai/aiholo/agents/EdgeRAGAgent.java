package oracleai.aiholo.agents;

import oracleai.vectorrag.service.VectorRAGServiceEdge;
import org.springframework.stereotype.Component;

/**
 * Edge RAG Agent that performs vector search and RAG queries
 * using Oracle Database 23ai's generate_text_response_all_docs function.
 * 
 * This agent leverages edge-based processing where:
 * - Vector embeddings are generated using TINYBERT_MODEL in Oracle
 * - Similarity search happens within the database
 * - LLM inference is performed by Ollama (llama3.2:3b)
 * - All processing is coordinated by a single SQL function call
 * 
 * This provides a lightweight, efficient alternative to the full Spring AI stack.
 */
@Component
public class EdgeRAGAgent implements Agent {
    
    private final VectorRAGServiceEdge vectorRAGServiceEdge;
    
    public EdgeRAGAgent(VectorRAGServiceEdge vectorRAGServiceEdge) {
        this.vectorRAGServiceEdge = vectorRAGServiceEdge;
    }
    
    @Override
    public String getName() {
        return "Edge RAG Agent";
    }

    @Override
    public String getValueName() {
        return "edgerag";
    }

    @Override
    public String[][] getKeywords() {
        // Triggers on document-related queries, similar to OracleDocAgent
        return new String[][] {
            {"edge", "search"},
            {"edge", "documents"},
            {"edge", "query"},
            {"vector", "database"},
            {"oracle", "vector"},
            {"database", "search"},
            {"rechercher" , "edge"}
        };
    }

    @Override
    public boolean isConfigured() {
        return vectorRAGServiceEdge != null && vectorRAGServiceEdge.isConfigured();
    }

    @Override
    public String processQuestion(String question) {
        System.out.println("Edge RAG Agent processing: " + question);
        
        if (!isConfigured()) {
            return "I'm sorry, the Edge RAG feature is not available right now. Please check the database configuration.";
        }
        
        try {
            // Use VectorRAGServiceEdge to perform RAG query via Oracle function
            String answer = vectorRAGServiceEdge.rag(question);
            System.out.println("Edge RAG Agent successfully processed query via generate_text_response_all_docs");
            return answer;
            
        } catch (Exception e) {
            System.err.println("Error in Edge RAG Agent: " + e.getMessage());
            e.printStackTrace();
            return "I'm having difficulty with the Edge RAG query right now. Let me help you with something else instead.";
        }
    }
}
