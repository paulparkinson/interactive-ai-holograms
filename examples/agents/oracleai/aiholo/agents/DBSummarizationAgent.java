package oracleai.aiholo.agents;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * DB Summarization Agent that performs document summarization
 * using Oracle Database 23ai's DBMS_VECTOR_CHAIN.
 *
 * This agent leverages:
 * - DBMS_VECTOR_CHAIN.UTL_TO_SUMMARY for in-database text summarization
 * - Oracle Database 23ai vector chain processing
 * - All summarization processing stays within the database
 *
 * Prerequisites:
 * - Oracle Database 23ai with DBMS_VECTOR_CHAIN package available
 * - An LLM credential configured (e.g., OCI GenAI, or a third-party provider)
 */
@Component
public class DBSummarizationAgent implements Agent {

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    @Override
    public String getName() {
        return "DB Summarization Agent";
    }

    @Override
    public String getValueName() {
        return "dbsummarizationagent";
    }

    @Override
    public String[][] getKeywords() {
        return new String[][] {
            {"summarize"},
            {"summary"},
            {"summarise"},
            {"condense", "document"},
            {"tldr"}
        };
    }

    @Override
    public boolean isConfigured() {
        return jdbcTemplate != null;
    }

    @Override
    public boolean preserveOriginalQuestion() {
        return true;
    }

    @Override
    public String processQuestion(String question) {
        System.out.println("DB Summarization Agent processing: " + question);

        if (!isConfigured()) {
            return "I'm sorry, the summarization feature is not available right now. Please check the database configuration.";
        }

        try {
            // Use DBMS_VECTOR_CHAIN.UTL_TO_SUMMARY for in-database summarization
            // The text to summarize is passed as the question; in a real deployment
            // you would retrieve the document content from the database first.
            String sql = """
                SELECT DBMS_VECTOR_CHAIN.UTL_TO_SUMMARY(
                    ?,
                    JSON('{"provider":"database", "glevel":"paragraph", "numParagraphs":2}')
                ) AS summary FROM dual
                """;
            String response = jdbcTemplate.queryForObject(sql, String.class, question);

            System.out.println("DB Summarization Agent successfully processed summarization request");
            return response != null ? response : "No summary could be generated.";

        } catch (Exception e) {
            System.err.println("Error in DB Summarization Agent: " + e.getMessage());
            e.printStackTrace();
            return "I'm having difficulty summarizing right now. " +
                   "Ensure DBMS_VECTOR_CHAIN is configured. Error: " + e.getMessage();
        }
    }
}
