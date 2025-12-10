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
import java.util.Arrays;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Oracle Document Agent that performs vector search on the documents table
 * and provides RAG (Retrieval Augmented Generation) responses using ChatGPT.
 * 
 * This agent:
 * 1. Connects to Oracle Database using the configured DataSource
 * 2. Uses Oracle's built-in VECTOR_EMBEDDING() function for consistency with Python app.py
 * 3. Performs vector similarity search on the documents table using COSINE distance
 * 4. Retrieves relevant document chunks based on the question
 * 5. Uses ChatGPT to generate contextual answers from the retrieved documents
 */
public class OracleDocAgent implements Agent {
    
    private final DataSource dataSource;
    private final ChatGPTService chatGPTService;
    private static final int MAX_RESULTS = 5; // Number of similar documents to retrieve
    private static double SIMILARITY_THRESHOLD = 0.3; // Similarity score threshold (0-1 scale) - lowered for text search
    
    // Oracle model configuration - should match the model imported via Python app.py
    // Python app.py uses "all_MiniLM_L12_v2" and converts to uppercase: "ALL_MINILM_L12_V2"
    private static final String ORACLE_MODEL_NAME = System.getenv().getOrDefault("ORACLE_MODEL_NAME", "ALL_MINILM_L12_V2");
    
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
            {"find", "documents"},
            {"rechercher" , "documents"}
        };
    }

    @Override
    public boolean isConfigured() {
        // Only need DataSource - embeddings are generated via Oracle's VECTOR_EMBEDDING() function
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
                // Convert to similarity score if needed (chunk.distance is now similarity score 0-1)
                double similarity = chunk.distance; // Already a similarity score
                if (similarity >= SIMILARITY_THRESHOLD) {
                    relevantDocs.add(chunk);
                } else {
                    System.out.println("Filtered out chunk from " + chunk.name + 
                                     " (similarity: " + String.format("%.4f", similarity) + 
                                     " < threshold: " + SIMILARITY_THRESHOLD + ")");
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
            
            // Only append sources if the answer actually used the documents
            // Skip sources if ChatGPT says documents don't contain the information
            String lowerAnswer = answer.toLowerCase();
            boolean documentsUsed = !lowerAnswer.contains("do not contain") && 
                                   !lowerAnswer.contains("don't contain") &&
                                   !lowerAnswer.contains("does not contain") &&
                                   !lowerAnswer.contains("doesn't contain") &&
                                   !lowerAnswer.contains("no information");
            
            String finalAnswer = answer;
            if (documentsUsed) {
                // Only show the top (most relevant) source document
                String topSource = getTopSourceName(relevantDocs);
                finalAnswer = answer + "\n\nSource: " + topSource;
            }
            
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
     * Search for similar documents using Oracle's VECTOR_EMBEDDING() function
     * This matches the Python app.py oracle_native approach for maximum consistency
     */
    private List<DocumentChunk> searchDocuments(String question) throws Exception {
        List<DocumentChunk> results = new ArrayList<>();
        
        // First try using Oracle's VECTOR_EMBEDDING() function
        try {
            // Check if models are available first
            checkAvailableModels();
            
            // Try different VECTOR_EMBEDDING syntaxes
            String[] sqlVariants = {
                // Variant 1: Model name as parameter
                """
                SELECT id, name, chunk_index, text, 
                       ROUND(1 - VECTOR_DISTANCE(embedding, VECTOR_EMBEDDING(? USING ? as data), COSINE) / 2, 6) as similarity_score
                FROM documents
                ORDER BY similarity_score DESC
                FETCH FIRST ? ROWS ONLY
                """,
                // Variant 2: Model name directly in SQL (not parameterized)
                String.format("""
                SELECT id, name, chunk_index, text, 
                       ROUND(1 - VECTOR_DISTANCE(embedding, VECTOR_EMBEDDING(%s USING ? as data), COSINE) / 2, 6) as similarity_score
                FROM documents
                ORDER BY similarity_score DESC
                FETCH FIRST ? ROWS ONLY
                """, ORACLE_MODEL_NAME)
            };
            
            boolean vectorSearchSuccessful = false;
            
            for (int i = 0; i < sqlVariants.length && !vectorSearchSuccessful; i++) {
                try (Connection conn = dataSource.getConnection();
                     PreparedStatement pstmt = conn.prepareStatement(sqlVariants[i])) {
                    
                    System.out.println("Trying VECTOR_EMBEDDING search variant " + (i+1) + " with model: " + ORACLE_MODEL_NAME);
                    
                    if (i == 0) {
                        // Variant 1: Both model and question as parameters
                        pstmt.setString(1, ORACLE_MODEL_NAME);
                        pstmt.setString(2, question);
                        pstmt.setInt(3, MAX_RESULTS);
                    } else {
                        // Variant 2: Only question as parameter (model name in SQL)
                        pstmt.setString(1, question);
                        pstmt.setInt(2, MAX_RESULTS);
                    }
                
                    try (ResultSet rs = pstmt.executeQuery()) {
                        while (rs.next()) {
                            String text = rs.getString("text");
                            DocumentChunk chunk = new DocumentChunk(
                                rs.getString("id"),
                                rs.getString("name"),
                                rs.getInt("chunk_index"),
                                text,
                                rs.getDouble("similarity_score")
                            );
                            results.add(chunk);
                            System.out.println("Found vector chunk (variant " + (i+1) + "): " + chunk.name + " (similarity: " + 
                                             String.format("%.4f", chunk.distance) + ")");
                        }
                    }
                    
                    // If we got results, mark as successful and break
                    if (!results.isEmpty()) {
                        vectorSearchSuccessful = true;
                        System.out.println("VECTOR_EMBEDDING search successful with variant " + (i+1));
                        break;
                    }
                } catch (Exception e) {
                    String errorMsg = e.getMessage();
                    System.err.println("VECTOR_EMBEDDING variant " + (i+1) + " failed: " + errorMsg);
                    if (errorMsg != null && errorMsg.contains("ORA-40281")) {
                        System.err.println("Model '" + ORACLE_MODEL_NAME + "' not found or not properly imported.");
                    } else if (errorMsg != null && errorMsg.contains("ORA-51808")) {
                        System.err.println("Vector dimension mismatch. The model produces different dimensions than stored embeddings.");
                        System.err.println("This suggests the documents were indexed with a different model than " + ORACLE_MODEL_NAME);
                    }
                    // Continue to next variant
                }
            }
            
            // If we got results from any variant, return them
            if (vectorSearchSuccessful && !results.isEmpty()) {
                return results;
            }
        } catch (Exception e) {
            String errorMsg = e.getMessage();
            System.err.println("All VECTOR_EMBEDDING variants failed: " + errorMsg);
            System.out.println("Falling back to text-based search...");
        }
        
        // Fallback: Text-based search with multiple strategies
        System.out.println("Using text-based search with keywords: " + extractKeywords(question));
        
        // Strategy 1: Search for all keywords together
        String allKeywords = extractKeywords(question);
        System.out.println("Extracted keywords: '" + allKeywords + "'");
        
        // Special handling for specific Oracle services
        if (question.toLowerCase().contains("fast connect") || question.toLowerCase().contains("fastconnect")) {
            // Try specific FastConnect terms first
            results.addAll(performTextSearch("%fastconnect%", "fastconnect (one word)"));
            if (results.isEmpty()) {
                results.addAll(performTextSearch("%fast connect%", "fast connect (two words)"));
            }
            if (results.isEmpty()) {
                results.addAll(performTextSearch("%networking%", "networking"));
            }
        } else if (question.toLowerCase().contains("dedicated region") || question.toLowerCase().contains("oci dedicated")) {
            // Try dedicated region specific terms
            results.addAll(performTextSearch("%dedicated region%", "dedicated region"));
            if (results.isEmpty()) {
                results.addAll(performTextSearch("%oci dedicated%", "oci dedicated"));
            }
            if (results.isEmpty()) {
                results.addAll(performTextSearch("%dedicated%", "dedicated"));
            }
        } else {
            // General search - try exact phrase first
            results.addAll(performTextSearch("%" + allKeywords + "%", "all keywords"));
        }
        
        // Strategy 2: Search for individual important keywords if no results from strategy 1
        if (results.isEmpty()) {
            String[] individualKeywords = allKeywords.split("\\s+");
            for (String keyword : individualKeywords) {
                if (keyword.length() > 2) {  // Skip very short words
                    List<DocumentChunk> keywordResults = performTextSearch("%" + keyword + "%", "keyword: " + keyword);
                    
                    // Only add if we don't have results, or if this keyword seems more relevant
                    if (results.isEmpty()) {
                        results.addAll(keywordResults);
                    }
                    
                    if (results.size() >= MAX_RESULTS) break;  // Don't get too many
                }
            }
        }
        
        // Strategy 3: Broader search if still no results
        if (results.isEmpty()) {
            // Try searching for just "connect" if the question contains "fast connect"
            if (question.toLowerCase().contains("connect")) {
                results.addAll(performTextSearch("%connect%", "broad search: connect"));
            }
        }
        
        // If no results found, check if there are any documents at all
        if (results.isEmpty()) {
            checkDocumentCount();
        }
        
        return results;
    }
    
    /**
     * Perform a text search with the given pattern and strategy description
     */
    private List<DocumentChunk> performTextSearch(String searchPattern, String strategy) {
        List<DocumentChunk> results = new ArrayList<>();
        
        String textSql = """
            SELECT id, name, chunk_index, text, 
                   0.8 as similarity_score
            FROM documents
            WHERE UPPER(text) LIKE UPPER(?) OR UPPER(name) LIKE UPPER(?)
            ORDER BY 
                CASE 
                    WHEN UPPER(text) LIKE UPPER(?) THEN 1
                    WHEN UPPER(name) LIKE UPPER(?) THEN 2
                    ELSE 3
                END
            FETCH FIRST ? ROWS ONLY
            """;
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(textSql)) {
            
            System.out.println("Trying " + strategy + " with pattern: " + searchPattern);
            
            pstmt.setString(1, searchPattern);
            pstmt.setString(2, searchPattern);
            pstmt.setString(3, searchPattern);
            pstmt.setString(4, searchPattern);
            pstmt.setInt(5, MAX_RESULTS);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String text = rs.getString("text");
                    DocumentChunk chunk = new DocumentChunk(
                        rs.getString("id"),
                        rs.getString("name"),
                        rs.getInt("chunk_index"),
                        text,
                        rs.getDouble("similarity_score")
                    );
                    results.add(chunk);
                    System.out.println("Found text match (" + strategy + "): " + chunk.name + " (chunk " + chunk.chunkIndex + ")");
                    System.out.println("  Preview: " + (text != null && text.length() > 100 ? 
                                     text.substring(0, 100) + "..." : text));
                }
            }
        } catch (Exception e) {
            System.err.println("Text search failed (" + strategy + "): " + e.getMessage());
        }
        
        return results;
    }
    
    /**
     * Extract meaningful keywords from question, preserving technical terms
     */
    private String extractKeywords(String question) {
        if (question == null || question.trim().isEmpty()) {
            return "";
        }
        
        // Remove common noise patterns
        String clean = question
            .replaceAll("(?i)Q\\d+:\\s*", "")  // Remove "Q1:", "Q2:", etc.
            .replaceAll("(?i)Q:\\s*", "")      // Remove "Q:"
            .replaceAll("(?i)\\. Respond in \\d+ words or less\\.*", "")  // Remove response instructions
            .replaceAll("\\s+", " ")          // Normalize whitespace
            .trim();
        
        // Split into words and filter more intelligently
        String[] words = clean.split("\\s+");
        List<String> keywords = new ArrayList<>();
        
        // Define stop words but preserve technical terms
        Set<String> stopWords = Set.of("the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for", "of", "with", "by", "is", "are", "was", "were", "what", "how", "why", "when", "where", "tell", "me", "more", "about");
        
        // Technical terms to always preserve
        Set<String> technicalTerms = Set.of("oci", "oracle", "cloud", "dedicated", "region", "fastconnect", "fast", "connect", "database", "ai", "vector", "search", "documentation");
        
        for (String word : words) {
            String lowerWord = word.toLowerCase().replaceAll("[^a-zA-Z0-9]", "");
            
            // Keep technical terms regardless of length
            if (technicalTerms.contains(lowerWord)) {
                keywords.add(lowerWord);
            }
            // Keep other words if they're not stop words and long enough
            else if (lowerWord.length() > 2 && !stopWords.contains(lowerWord)) {
                keywords.add(lowerWord);
            }
        }
        
        // Return the most important keywords (up to 5)
        return keywords.stream()
            .limit(5)
            .reduce((a, b) -> a + " " + b)
            .orElse(clean);
    }
    
    /**
     * Check if there are any documents in the database for debugging
     */
    private void checkDocumentCount() {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement("SELECT COUNT(*) as doc_count FROM documents")) {
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    int count = rs.getInt("doc_count");
                    System.out.println("Total documents in database: " + count);
                    if (count == 0) {
                        System.out.println("No documents found in database. Please upload documents via Python app.py first.");
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to check document count: " + e.getMessage());
        }
    }
    
    /**
     * Check what ONNX models are available in the database
     */
    private void checkAvailableModels() {
        try (Connection conn = dataSource.getConnection()) {
            String modelCheckSql = "SELECT model_name FROM user_mining_models ORDER BY model_name";
            try (PreparedStatement pstmt = conn.prepareStatement(modelCheckSql);
                 ResultSet rs = pstmt.executeQuery()) {
                
                System.out.println("Available ONNX models in database:");
                boolean hasModels = false;
                while (rs.next()) {
                    String modelName = rs.getString("model_name");
                    System.out.println("  - " + modelName);
                    hasModels = true;
                }
                if (!hasModels) {
                    System.out.println("  No ONNX models found. Use Python app.py /setup/import-onnx-model to import.");
                }
            }
        } catch (Exception e) {
            System.err.println("Could not check available models: " + e.getMessage());
        }
    }
    
    // Note: Embedding generation is now handled by Oracle's VECTOR_EMBEDDING() function
    // No local embedding generation methods needed
    
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
     * Get the top (most relevant) source document name
     */
    private String getTopSourceName(List<DocumentChunk> chunks) {
        if (chunks.isEmpty()) {
            return "Unknown";
        }
        // Since chunks are already sorted by distance (closest first), return the first one's name
        return chunks.get(0).name;
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
