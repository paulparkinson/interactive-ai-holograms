package oracleai.aiholo.agents;

import oracleai.vectorrag.service.VectorRAGService;
import org.springframework.stereotype.Component;

/**
 * Spring AI Vector RAG Agent that performs vector search and RAG queries
 * using the Spring AI VectorStore integration with Oracle 23ai.
 *
 * This agent uses:
 * - Spring AI VectorStore for similarity search with OpenAI embeddings
 * - Spring AI ChatClient for LLM response generation (external OpenAI)
 * - Oracle Database 23ai as the vector store backend
 */
@Component
public class SpringAIVectorRAGAgent implements Agent {

    private final VectorRAGService vectorRAGService;

    public SpringAIVectorRAGAgent(VectorRAGService vectorRAGService) {
        this.vectorRAGService = vectorRAGService;
    }

    @Override
    public String getName() {
        return "Spring AI Vector RAG Agent";
    }

    @Override
    public String getValueName() {
        return "springaivectorrag";
    }

    @Override
    public String[][] getKeywords() {
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
        System.out.println("Spring AI Vector RAG Agent processing: " + question);

        if (!isConfigured()) {
            return "I'm sorry, the document search feature is not available right now. Please check the configuration.";
        }

        try {
            String answer = vectorRAGService.rag(question);
            System.out.println("Spring AI Vector RAG Agent successfully processed query via VectorRAGService");
            return answer;

        } catch (Exception e) {
            System.err.println("Error in Spring AI Vector RAG Agent: " + e.getMessage());
            e.printStackTrace();
            return "I'm having difficulty searching the documents right now. Let me help you with something else instead.";
        }
    }
}
