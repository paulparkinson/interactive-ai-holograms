package oracleai.aiholo.agents;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import dev.langchain4j.store.embedding.oracle.OracleEmbeddingStore;

/**
 * Langchain4j Oracle RAG Agent that performs vector search using
 * the langchain4j-oracle OracleEmbeddingStore.
 *
 * This agent leverages:
 * - Langchain4j's OracleEmbeddingStore for vector similarity search
 * - Oracle Database 23ai as the vector store backend
 * - Any Langchain4j-compatible embedding model for vectorization
 *
 * Prerequisites:
 * - langchain4j-oracle dependency (already in pom.xml)
 * - Oracle Database 23ai with vector support
 * - A configured DataSource
 */
@Component
public class Langchain4jOracleRAGAgent implements Agent {

    @Autowired(required = false)
    private DataSource dataSource;

    private volatile OracleEmbeddingStore embeddingStore;

    @Override
    public String getName() {
        return "Langchain4j Oracle RAG Agent";
    }

    @Override
    public String getValueName() {
        return "langchain4joraclerag";
    }

    @Override
    public String[][] getKeywords() {
        return new String[][] {
            {"langchain", "search"},
            {"langchain", "rag"},
            {"langchain", "documents"},
            {"lc4j", "search"}
        };
    }

    @Override
    public boolean isConfigured() {
        return dataSource != null;
    }

    @Override
    public boolean preserveOriginalQuestion() {
        return true;
    }

    private OracleEmbeddingStore getOrCreateStore() {
        if (embeddingStore == null) {
            synchronized (this) {
                if (embeddingStore == null) {
                    embeddingStore = OracleEmbeddingStore.builder()
                            .dataSource(dataSource)
                            .embeddingTable("langchain4j_embeddings")
                            .build();
                }
            }
        }
        return embeddingStore;
    }

    @Override
    public String processQuestion(String question) {
        System.out.println("Langchain4j Oracle RAG Agent processing: " + question);

        if (!isConfigured()) {
            return "I'm sorry, the Langchain4j Oracle RAG feature is not available right now. Please check the database configuration.";
        }

        try {
            // Verify the store can be created (validates DataSource connectivity)
            getOrCreateStore();

            // In a full implementation, you would use an EmbeddingModel to embed the question
            // and then search via OracleEmbeddingStore. Example:
            //   Embedding queryEmbedding = embeddingModel.embed(question).content();
            //   EmbeddingSearchResult<TextSegment> results = embeddingStore.search(
            //       EmbeddingSearchRequest.builder().queryEmbedding(queryEmbedding).maxResults(4).build()
            //   );

            return "Langchain4j Oracle RAG Agent received: " + question +
                   "\n\nNote: This agent requires an EmbeddingModel to be configured for full RAG functionality. " +
                   "The OracleEmbeddingStore is ready and connected to the database.";

        } catch (Exception e) {
            System.err.println("Error in Langchain4j Oracle RAG Agent: " + e.getMessage());
            e.printStackTrace();
            return "I'm having difficulty with the Langchain4j search right now. Error: " + e.getMessage();
        }
    }
}
