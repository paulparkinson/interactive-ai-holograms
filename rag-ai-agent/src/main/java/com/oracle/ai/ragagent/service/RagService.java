package com.oracle.ai.ragagent.service;

import com.oracle.ai.ragagent.config.OracleDatabaseProperties;
import com.oracle.ai.ragagent.config.RagProperties;
import com.oracle.ai.ragagent.config.VertexAiProperties;
import lombok.extern.slf4j.Slf4j;
import oracle.jdbc.OracleConnection;
import oracle.jdbc.pool.OracleDataSource;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Service for executing RAG queries against Oracle Database 23ai Vector Store
 * with Vertex AI Gemini LLM
 */
@Slf4j
@Service
public class RagService {

    private final OracleDatabaseProperties dbProps;
    private final VertexAiProperties vertexProps;
    private final RagProperties ragProps;
    private final VertexAiService vertexAiService;
    
    private OracleDataSource dataSource;

    public RagService(
            OracleDatabaseProperties dbProps,
            VertexAiProperties vertexProps,
            RagProperties ragProps,
            VertexAiService vertexAiService) {
        this.dbProps = dbProps;
        this.vertexProps = vertexProps;
        this.ragProps = ragProps;
        this.vertexAiService = vertexAiService;
    }

    @PostConstruct
    public void init() throws SQLException {
        log.info("Initializing Oracle Database connection to {}", dbProps.getDsn());
        
        // Create Oracle DataSource with wallet
        dataSource = new OracleDataSource();
        dataSource.setURL("jdbc:oracle:thin:@" + dbProps.getDsn() + "?TNS_ADMIN=" + 
                         expandPath(dbProps.getWalletLocation()));
        dataSource.setUser(dbProps.getUsername());
        dataSource.setPassword(dbProps.getPassword());
        
        // Configure connection properties
        Properties props = new Properties();
        props.put(OracleConnection.CONNECTION_PROPERTY_WALLET_LOCATION, 
                 expandPath(dbProps.getWalletLocation()));
        props.put(OracleConnection.CONNECTION_PROPERTY_WALLET_PASSWORD, 
                 dbProps.getWalletPassword());
        
        dataSource.setConnectionProperties(props);
        
        log.info("Oracle Database connection initialized successfully");
    }

    /**
     * Execute a RAG query: retrieve relevant context from vector DB and generate response
     */
    public String executeRagQuery(String question) throws Exception {
        long startTime = System.currentTimeMillis();
        
        log.info("Executing RAG query: {}", question);
        
        // Step 1: Generate embedding for the question
        log.debug("Generating embedding for question");
        List<Float> questionEmbedding = vertexAiService.generateEmbedding(question);
        
        // Step 2: Perform vector similarity search in Oracle Database
        log.debug("Performing vector similarity search in Oracle Database");
        List<String> relevantChunks = performVectorSearch(questionEmbedding, ragProps.getRetriever().getTopK());
        
        // Step 3: Build context from retrieved chunks
        String context = String.join("\n\n", relevantChunks);
        log.debug("Retrieved {} chunks with total length: {}", relevantChunks.size(), context.length());
        
        // Step 4: Generate response using Gemini LLM
        log.debug("Generating response with Gemini LLM");
        String prompt = ragProps.getPromptTemplate()
            .replace("{context}", context)
            .replace("{question}", question);
        
        String response = vertexAiService.generateResponse(prompt);
        
        long duration = System.currentTimeMillis() - startTime;
        log.info("RAG query completed in {} ms", duration);
        
        return response;
    }

    /**
     * Perform vector similarity search in Oracle Database
     */
    private List<String> performVectorSearch(List<Float> queryEmbedding, int topK) throws SQLException {
        List<String> results = new ArrayList<>();
        
        // Convert embedding to Oracle VECTOR format
        String vectorString = "[" + queryEmbedding.stream()
            .map(String::valueOf)
            .reduce((a, b) -> a + "," + b)
            .orElse("") + "]";
        
        // SQL query using Oracle VECTOR_DISTANCE for similarity search
        String sql = String.format(
            "SELECT cmetadata, cblob " +
            "FROM %s " +
            "ORDER BY VECTOR_DISTANCE(embedding, TO_VECTOR(?, *, FLOAT32), DOT_PRODUCT) " +
            "FETCH FIRST ? ROWS ONLY",
            dbProps.getVectorTable()
        );
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, vectorString);
            stmt.setInt(2, topK);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String content = rs.getString("cblob");
                    if (content != null && !content.isEmpty()) {
                        results.add(content);
                    }
                }
            }
        }
        
        log.debug("Vector search returned {} results", results.size());
        return results;
    }

    /**
     * Expand ~ in path to user home directory
     */
    private String expandPath(String path) {
        if (path.startsWith("~/")) {
            return System.getProperty("user.home") + path.substring(1);
        }
        return path;
    }

    /**
     * Health check - test database connectivity
     */
    public boolean isHealthy() {
        try (Connection conn = dataSource.getConnection()) {
            return conn.isValid(5);
        } catch (SQLException e) {
            log.error("Database health check failed", e);
            return false;
        }
    }
}
