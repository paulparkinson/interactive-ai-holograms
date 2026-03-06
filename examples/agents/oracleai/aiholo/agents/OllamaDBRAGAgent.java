package oracleai.aiholo.agents;

import oracleai.vectorrag.service.VectorRAGServiceEdge;
import org.springframework.stereotype.Component;

/**
 * Ollama DB RAG Agent that performs vector search and RAG queries
 * using Oracle Database 23ai's generate_text_response_all_docs function.
 *
 * This agent leverages in-database processing where:
 * - Vector embeddings are generated using TINYBERT_MODEL in Oracle
 * - Similarity search happens within the database
 * - LLM inference is performed by Ollama (llama3.2:3b)
 * - All processing is coordinated by a single SQL function call
 *
 * This provides a lightweight, efficient alternative to the full Spring AI stack.
 */
@Component
public class OllamaDBRAGAgent implements Agent {

    private final VectorRAGServiceEdge vectorRAGServiceEdge;

    public OllamaDBRAGAgent(VectorRAGServiceEdge vectorRAGServiceEdge) {
        this.vectorRAGServiceEdge = vectorRAGServiceEdge;
    }

    @Override
    public String getName() {
        return "Ollama DB RAG Agent";
    }

    @Override
    public String getValueName() {
        return "ollamadbrag";
    }

    @Override
    public String[][] getKeywords() {
        return new String[][] {
            {"search", "docs"},
            {"search", "documentation"},
            {"doc", "agent"},
            {"documentation", "agent"},
            {"ollama", "search"},
            {"ollama", "documents"},
            {"ollama", "doc"},
            {"rechercher" , "ollama"}
        };
    }

    @Override
    public boolean isConfigured() {
        return vectorRAGServiceEdge != null && vectorRAGServiceEdge.isConfigured();
    }

    @Override
    public String processQuestion(String question) {
        System.out.println("Ollama DB RAG Agent processing: " + question);

        if (!isConfigured()) {
            return "I'm sorry, the Ollama DB RAG feature is not available right now. Please check the database configuration.";
        }

        try {
            String answer = vectorRAGServiceEdge.rag(question);
            System.out.println("Ollama DB RAG Agent successfully processed query via generate_text_response_all_docs");
            return answer;

        } catch (Exception e) {
            System.err.println("Error in Ollama DB RAG Agent: " + e.getMessage());
            e.printStackTrace();
            return "I'm having difficulty with the Ollama DB RAG query right now. Let me help you with something else instead.";
        }
    }
}
