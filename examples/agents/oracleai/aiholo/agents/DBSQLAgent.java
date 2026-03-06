package oracleai.aiholo.agents;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * DB SQL Agent that performs natural-language-to-SQL queries
 * using Oracle Database 23ai's DBMS_CLOUD_AI.
 *
 * This agent leverages:
 * - DBMS_CLOUD_AI.GENERATE to translate natural language questions into SQL
 * - Oracle Database executes the generated SQL and returns results
 * - All processing stays within the database
 *
 * Prerequisites:
 * - DBMS_CLOUD_AI configured with an AI profile (e.g., OPENAI, OCI_GENAI)
 * - The AI profile must be set as the current profile in the session
 *   or passed to DBMS_CLOUD_AI.GENERATE
 */
@Component
public class DBSQLAgent implements Agent {

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    @Override
    public String getName() {
        return "DB SQL Agent";
    }

    @Override
    public String getValueName() {
        return "dbsqlagent";
    }

    @Override
    public String[][] getKeywords() {
        return new String[][] {
            {"sql", "query"},
            {"database", "query"},
            {"ask", "database"},
            {"natural", "language", "sql"},
            {"nl2sql"},
            {"cloud", "ai", "query"}
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
        System.out.println("DB SQL Agent processing: " + question);

        if (!isConfigured()) {
            return "I'm sorry, the DB SQL feature is not available right now. Please check the database configuration.";
        }

        try {
            // Use DBMS_CLOUD_AI.GENERATE to translate natural language to SQL and execute
            // action => 'narrate' returns a natural language answer
            // action => 'showsql' returns the generated SQL
            // action => 'runsql' returns raw SQL results
            String sql = "SELECT DBMS_CLOUD_AI.GENERATE(prompt => ?, action => 'narrate') AS response FROM dual";
            String response = jdbcTemplate.queryForObject(sql, String.class, question);

            System.out.println("DB SQL Agent successfully processed NL2SQL query");
            return response != null ? response : "No response received from DBMS_CLOUD_AI.";

        } catch (Exception e) {
            System.err.println("Error in DB SQL Agent: " + e.getMessage());
            e.printStackTrace();
            return "I'm having difficulty with the database query right now. " +
                   "Ensure DBMS_CLOUD_AI is configured with a valid AI profile. Error: " + e.getMessage();
        }
    }
}
