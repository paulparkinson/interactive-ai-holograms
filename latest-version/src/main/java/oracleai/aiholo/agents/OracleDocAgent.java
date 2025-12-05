package oracleai.aiholo.agents;

import oracleai.aiholo.ChatGPTService;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * Oracle Document Agent that performs vector search on the documents table
 * and provides RAG (Retrieval Augmented Generation) responses using ChatGPT.
 * 
 * This agent:
 * 1. Connects to Oracle Database using the configured DataSource
 * 2. Generates embeddings using OpenAI's embedding API (text-embedding-3-small)
 * 3. Performs vector similarity search on the documents table using COSINE distance
 * 4. Retrieves relevant document chunks based on the question
 * 5. Uses ChatGPT to generate contextual answers from the retrieved documents
 */
public class OracleDocAgent implements Agent {
    
    private final DataSource dataSource;
    private final ChatGPTService chatGPTService;
    private static final int MAX_RESULTS = 5; // Number of similar documents to retrieve
    private static double SIMILARITY_THRESHOLD = 0.90; // Only use chunks with distance <= 0.90 (lower = more similar)
    private static final String OPENAI_EMBEDDING_URL = "https://api.openai.com/v1/embeddings";
    private static final String EMBEDDING_MODEL = "text-embedding-3-small";
    private final RestTemplate restTemplate = new RestTemplate();
    
    // Getter and setter for similarity threshold
    public static double getSimilarityThreshold() {
        return SIMILARITY_THRESHOLD;
    }
    
    public static void setSimilarityThreshold(double threshold) {
        SIMILARITY_THRESHOLD = threshold;
        System.out.println("Document similarity threshold updated to: " + threshold);
    }
    
    public OracleDocAgent(DataSource dataSource, ChatGPTService chatGPTService) {
        this.dataSource = dataSource;
        this.chatGPTService = chatGPTService;
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
            {"find", "documents"}
        };
    }

    @Override
    public boolean isConfigured() {
        // Only need DataSource - embeddings are generated in Oracle Database
        return dataSource != null;
    }

    @Override
    public String processQuestion(String question) {
        System.out.println("Oracle Document Agent processing: " + question);
        
        if (!isConfigured()) {
            return "I'm sorry, the document search feature is not available right now. Please check the database configuration.";
        }
        
        try {
            // Step 1: Perform vector similarity search using OpenAI embeddings
            List<DocumentChunk> allDocs = searchDocuments(question);
            
            if (allDocs.isEmpty()) {
                return "I couldn't find any relevant documents for your question. Perhaps try rephrasing it?";
            }
            
            // Step 2: Filter by similarity threshold (only keep highly relevant chunks)
            List<DocumentChunk> relevantDocs = new ArrayList<>();
            for (DocumentChunk chunk : allDocs) {
                if (chunk.distance <= SIMILARITY_THRESHOLD) {
                    relevantDocs.add(chunk);
                } else {
                    System.out.println("Filtered out chunk from " + chunk.name + 
                                     " (distance: " + String.format("%.4f", chunk.distance) + 
                                     " > threshold: " + SIMILARITY_THRESHOLD + ")");
                }
            }
            
            if (relevantDocs.isEmpty()) {
                return "I couldn't find any sufficiently relevant documents for your question. The closest matches weren't similar enough. Perhaps try rephrasing it?";
            }
            
            // Step 3: Build context from retrieved documents
            String context = buildContext(relevantDocs);
            
            // Step 4: Use ChatGPT for RAG response
            String ragPrompt = buildRAGPrompt(question, context);
            
            // Debug: Print the full RAG prompt to see what we're sending
            System.out.println("=== RAG PROMPT START ===");
            System.out.println(ragPrompt);
            System.out.println("=== RAG PROMPT END ===");
            
            String answer = chatGPTService.queryChatGPT(ragPrompt, "gpt-4");
            
            // Append source document names to the answer
            String sources = getUniqueSourceNames(relevantDocs);
            String finalAnswer = answer + "\n\nSources: " + sources;
            
            System.out.println("Oracle Document Agent retrieved " + relevantDocs.size() + " relevant chunks (filtered from " + allDocs.size() + " total)");
            return finalAnswer;
            
        } catch (IllegalStateException e) {
            // OpenAI API key not configured
            System.err.println("Configuration error in Oracle Document Agent: " + e.getMessage());
            return "I'm unable to search documents right now due to a configuration issue. Please contact support.";
        } catch (java.sql.SQLException e) {
            System.err.println("Database connection error in Oracle Document Agent: " + e.getMessage());
            e.printStackTrace();
            // Check for specific connection errors
            if (e.getMessage().contains("Network Adapter") || e.getMessage().contains("UnknownHost")) {
                return "I'm unable to connect to the document database right now. The database may be offline or the connection settings need to be updated.";
            }
            return "I'm having trouble accessing the document database. Please try again in a moment.";
        } catch (Exception e) {
            System.err.println("Error in Oracle Document Agent: " + e.getMessage());
            e.printStackTrace();
            return "I'm having difficulty searching the documents right now. Let me help you with something else instead.";
        }
    }
    
    /**
     * Search for similar documents using OpenAI embeddings and Oracle vector distance
     */
    private List<DocumentChunk> searchDocuments(String question) throws Exception {
        List<DocumentChunk> results = new ArrayList<>();
        
        // Step 1: Generate embedding for the question using OpenAI API
        float[] questionEmbedding = generateEmbedding(question);
        
        // Step 2: Convert float array to Oracle VECTOR format string
        String vectorString = floatArrayToVectorString(questionEmbedding);
        
        // Step 3: Oracle 23ai vector similarity search
        String sql = """
            SELECT id, name, chunk_index, text, 
                   VECTOR_DISTANCE(embedding, ?, COSINE) as distance
            FROM documents
            ORDER BY distance
            FETCH FIRST ? ROWS ONLY
            """;
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            System.out.println("Executing vector similarity search...");
            
            // Set the vector parameter as a string representation
            pstmt.setString(1, vectorString);
            pstmt.setInt(2, MAX_RESULTS);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String text = rs.getString("text");
                    DocumentChunk chunk = new DocumentChunk(
                        rs.getString("id"),
                        rs.getString("name"),
                        rs.getInt("chunk_index"),
                        text,
                        rs.getDouble("distance")
                    );
                    results.add(chunk);
                    System.out.println("Found document chunk: " + chunk.name + 
                                     " (chunk " + chunk.chunkIndex + 
                                     ", distance: " + String.format("%.4f", chunk.distance) + ")");
                    System.out.println("  Text preview: " + (text != null && text.length() > 100 ? 
                                     text.substring(0, 100) + "..." : text));
                }
            }
        }
        
        return results;
    }
    
    /**
     * Generate embedding for text using OpenAI API
     */
    private float[] generateEmbedding(String text) throws Exception {
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isEmpty() || apiKey.startsWith("sk-YOUR_")) {
            throw new IllegalStateException("OPENAI_API_KEY environment variable not set");
        }
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        
        JSONObject requestBody = new JSONObject();
        requestBody.put("model", EMBEDDING_MODEL);
        requestBody.put("input", text);
        requestBody.put("dimensions", 512); // Match database vector dimension
        
        HttpEntity<String> request = new HttpEntity<>(requestBody.toString(), headers);
        
        System.out.println("Generating embedding using OpenAI " + EMBEDDING_MODEL + "...");
        String response = restTemplate.postForObject(OPENAI_EMBEDDING_URL, request, String.class);
        
        JSONObject jsonResponse = new JSONObject(response);
        JSONArray embeddingArray = jsonResponse.getJSONArray("data")
                                               .getJSONObject(0)
                                               .getJSONArray("embedding");
        
        float[] embedding = new float[embeddingArray.length()];
        for (int i = 0; i < embeddingArray.length(); i++) {
            embedding[i] = embeddingArray.getFloat(i);
        }
        
        System.out.println("Generated embedding with " + embedding.length + " dimensions");
        return embedding;
    }
    
    /**
     * Convert float array to Oracle VECTOR string format: [0.1,0.2,0.3,...]
     */
    private String floatArrayToVectorString(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(vector[i]);
        }
        sb.append("]");
        return sb.toString();
    }
    
    /**
     * Build context string from retrieved document chunks
     */
    private String buildContext(List<DocumentChunk> chunks) {
        StringBuilder context = new StringBuilder();
        context.append("Here are the relevant document excerpts:\n\n");
        
        for (int i = 0; i < chunks.size(); i++) {
            DocumentChunk chunk = chunks.get(i);
            context.append("Document ").append(i + 1).append(" (")
                   .append(chunk.name).append(" - Part ").append(chunk.chunkIndex).append("):\n");
            context.append(chunk.text).append("\n\n");
        }
        
        return context.toString();
    }
    
    /**
     * Build RAG prompt combining question and context
     */
    private String buildRAGPrompt(String question, String context) {
        return String.format(
            "Based on the following documents, please answer the question.\n\n" +
            "%s\n" +
            "Question: %s\n\n" +
            "Please provide a concise answer (50 words or less) based solely on the information " +
            "in the documents above. If the documents don't contain relevant information, " +
            "say so clearly.",
            context,
            question
        );
    }
    
    /**
     * Extract unique document names from the list of chunks
     */
    private String getUniqueSourceNames(List<DocumentChunk> chunks) {
        java.util.Set<String> uniqueNames = new java.util.LinkedHashSet<>();
        for (DocumentChunk chunk : chunks) {
            uniqueNames.add(chunk.name);
        }
        return String.join(", ", uniqueNames);
    }
    
    /**
     * Inner class to hold document chunk information
     */
    private static class DocumentChunk {
        final String id;
        final String name;
        final int chunkIndex;
        final String text;
        final double distance;
        
        DocumentChunk(String id, String name, int chunkIndex, String text, double distance) {
            this.id = id;
            this.name = name;
            this.chunkIndex = chunkIndex;
            this.text = text;
            this.distance = distance;
        }
    }
}
